package edu.utdallas.sentencebuilder;

import edu.utdallas.sentencebuilder.autocomplete.AutoCompleteService;
import edu.utdallas.sentencebuilder.config.DbSettings;
import edu.utdallas.sentencebuilder.corpus.ImportService;
import edu.utdallas.sentencebuilder.corpus.ProgressListener;
import edu.utdallas.sentencebuilder.db.Database;
import edu.utdallas.sentencebuilder.generate.GenerationOptions;
import edu.utdallas.sentencebuilder.generate.GeneratorFactory;
import edu.utdallas.sentencebuilder.generate.SentenceGenerator;
import edu.utdallas.sentencebuilder.lm.JdbcLanguageModel;
import edu.utdallas.sentencebuilder.model.Follower;
import edu.utdallas.sentencebuilder.model.GeneratedSentence;
import edu.utdallas.sentencebuilder.model.ImportedFile;
import edu.utdallas.sentencebuilder.model.WordRow;
import edu.utdallas.sentencebuilder.model.WordStats;
import edu.utdallas.sentencebuilder.repo.GeneratedSentenceRepository;
import edu.utdallas.sentencebuilder.repo.ImportedFileRepository;
import edu.utdallas.sentencebuilder.repo.WordRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of the whole pipeline through JDBC.  By default it runs
 * against an in-memory H2 database in MySQL-compatibility mode, so
 * {@code mvn test} needs no server.  To run it against a real MySQL server
 * pass a URL; it DROPS AND RECREATES the tables in that database:
 * <pre>
 * mvn test -Dsb.it.url="jdbc:mysql://localhost:3306/sentence_builder_test?createDatabaseIfNotExist=true&amp;rewriteBatchedStatements=true" \
 *          -Dsb.it.user=root -Dsb.it.password=secret
 * </pre>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseIntegrationTest {

    private static final String DEFAULT_URL =
            "jdbc:h2:mem:sb_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1";

    private static final String TEXT = """
            It is a truth universally acknowledged, that a single man in possession of a good
            fortune, must be in want of a wife.

            However little known the feelings or views of such a man may be on his first entering
            a neighbourhood, this truth is so well fixed in the minds of the surrounding families,
            that he is considered as the rightful property of some one or other of their daughters.

            "My dear Mr. Bennet," said his lady to him one day, "have you heard that Netherfield
            Park is let at last?"

            Mr. Bennet replied that he had not. "But it is," returned she. It is a truth.
            """;

    private static Database db;
    private static Path sample;

    @BeforeAll
    static void setUp() throws Exception {
        String url = System.getProperty("sb.it.url", DEFAULT_URL);
        DbSettings s = new DbSettings(url, System.getProperty("sb.it.user", url.equals(DEFAULT_URL) ? "sa" : "root"),
                System.getProperty("sb.it.password", ""));
        db = new Database(s);
        try (Connection c = db.connection(); Statement st = c.createStatement()) {
            // Children first, so no foreign key gets in the way.
            for (String t : List.of("generated_sentence", "word_follow", "imported_file", "word")) {
                st.execute("DROP TABLE IF EXISTS " + t);
            }
        }
        db.close();
        db = new Database(s);      // re-creates the schema
        sample = Files.createTempFile("sb-sample", ".txt");
        Files.writeString(sample, TEXT, StandardCharsets.UTF_8);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (db != null) {
            db.close();
        }
        if (sample != null) {
            Files.deleteIfExists(sample);
        }
    }

    @Test
    @Order(1)
    void importsAFileAndCountsAreRight() throws Exception {
        ImportService svc = new ImportService(db);
        ImportedFile f = svc.importFile(sample, ProgressListener.NONE);
        assertTrue(f.id() > 0);
        assertEquals(ImportedFile.SourceType.TEXT_FILE, f.sourceType());
        assertTrue(f.wordCount() > 80, "word count " + f.wordCount());
        assertEquals(f.distinctWords(), f.newWords(), "everything is new on an empty database");

        JdbcLanguageModel lm = new JdbcLanguageModel(db);
        WordStats it = lm.stats("it").orElseThrow();
        assertEquals(3, it.total());       // "It is a truth" x2, "But it is"
        assertEquals(2, it.start());
        List<Follower> afterIt = lm.followers("it");
        assertEquals("is", afterIt.get(0).word());
        assertEquals(3, afterIt.get(0).count());

        WordStats wife = lm.stats("wife").orElseThrow();
        assertEquals(1, wife.end());
        assertTrue(lm.stats("mr").orElseThrow().end() > 0, "abbreviation period counts as a sentence end");

        // Importing the same file again just adds to the counts.
        svc.importFile(sample, ProgressListener.NONE);
        assertEquals(6, lm.stats("it").orElseThrow().total());
        assertEquals(2, new ImportedFileRepository(db).all().size());
        assertTrue(new ImportedFileRepository(db).wasImported(sample.toAbsolutePath().toString()));
    }

    @Test
    @Order(2)
    void generatesSavesAndFeedsBack() throws Exception {
        JdbcLanguageModel lm = new JdbcLanguageModel(db);
        GeneratedSentenceRepository repo = new GeneratedSentenceRepository(db);
        List<SentenceGenerator> gens = GeneratorFactory.all(lm);
        assertEquals(4, gens.size());

        for (SentenceGenerator g : gens) {
            GeneratedSentence s = g.generate("it", GenerationOptions.DEFAULT, false);
            assertEquals("it", s.words().get(0));
            GeneratedSentence saved = repo.save(s);
            assertTrue(saved.id() > 0);
        }
        GeneratedSentence rnd = gens.get(0).generateFromRandomStart(GenerationOptions.DEFAULT);
        assertTrue(lm.stats(rnd.startWord()).orElseThrow().start() > 0);
        repo.save(rnd);
        repo.save(rnd);                                   // deliberate duplicate

        List<GeneratedSentence> all = repo.history(false, "", 100);
        assertEquals(6, all.size());
        List<GeneratedSentence> dups = repo.history(true, "", 100);
        assertTrue(dups.size() >= 2);
        assertTrue(dups.get(0).duplicates() >= 2);

        long before = lm.stats("it").orElseThrow().total();
        ImportedFile fed = new ImportService(db).importGenerated(all, ProgressListener.NONE);
        assertEquals(ImportedFile.SourceType.GENERATED, fed.sourceType());
        repo.markFedBack(all.stream().map(GeneratedSentence::id).toList(), fed.id());
        assertTrue(repo.history(false, "", 100).stream().allMatch(GeneratedSentence::fedBack));
        assertTrue(lm.stats("it").orElseThrow().total() > before);
        assertNotNull(lm.randomStartWord(new Random()).orElse(null));
    }

    @Test
    @Order(3)
    void autoCompleteLearnsFromTheUser() throws Exception {
        JdbcLanguageModel lm = new JdbcLanguageModel(db);
        AutoCompleteService ac = new AutoCompleteService(db, lm);

        assertFalse(ac.recordWord(null, "it"));                // known word, sentence start
        assertTrue(ac.recordWord("it", "zorbles"));            // brand-new word
        ac.recordSentenceEnd("zorbles");

        WordStats z = lm.stats("zorbles").orElseThrow();
        assertEquals(1, z.total());
        assertEquals(1, z.end());
        Follower f = ac.suggestions("it", 50).stream().filter(x -> x.word().equals("zorbles")).findFirst().orElseThrow();
        assertEquals(1, f.count());
        assertEquals(1, f.chosenCount());

        ac.recordWord("it", "is");                              // existing pair: both counters bump
        Follower is = ac.suggestions("it", 1).get(0);
        assertEquals("is", is.word());
        assertEquals(1, is.chosenCount());
        ac.recordSession(3, 1, 1);
        assertEquals(ImportedFile.SourceType.AUTOCOMPLETE, new ImportedFileRepository(db).all().get(0).sourceType());
    }

    @Test
    @Order(4)
    void wordRepositoryBrowsesAndEdits() throws Exception {
        WordRepository words = new WordRepository(db);
        List<WordRow> alpha = words.search("", 1, WordRepository.Sort.ALPHABETICAL, 10_000);
        assertTrue(alpha.size() > 50);
        for (int i = 1; i < alpha.size(); i++) {
            assertTrue(alpha.get(i - 1).text().compareTo(alpha.get(i).text()) < 0, "not alphabetical");
        }
        List<WordRow> freq = words.search("", 1, WordRepository.Sort.MOST_FREQUENT, 5);
        assertTrue(freq.get(0).total() >= freq.get(1).total());
        assertTrue(words.search("tru", 1, WordRepository.Sort.ALPHABETICAL, 10).stream()
                .allMatch(w -> w.text().startsWith("tru")));
        assertTrue(words.search("*ing", 1, WordRepository.Sort.ALPHABETICAL, 10).stream()
                .allMatch(w -> w.text().endsWith("ing")));

        WordRow truth = words.find("truth").orElseThrow();
        assertTrue(truth.followerCount() >= 1);
        words.updateCounts(truth.id(), 99, 7, 3);
        WordRow edited = words.find("truth").orElseThrow();
        assertEquals(99, edited.total());
        assertEquals(7, edited.start());
        assertEquals(3, edited.end());
        assertFalse(words.followers(truth.id()).isEmpty());
        assertFalse(words.predecessors(truth.id()).isEmpty());

        var summary = words.summary();
        assertEquals(alpha.size(), summary.distinctWords());
        assertTrue(summary.bigrams() > 0);
        assertEquals(4, summary.filesImported());
        assertEquals(6, summary.sentencesGenerated());
        assertTrue(summary.distinctSentencesGenerated() < 6);
    }
}
