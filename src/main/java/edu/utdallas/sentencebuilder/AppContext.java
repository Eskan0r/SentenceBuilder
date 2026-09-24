package edu.utdallas.sentencebuilder;

import edu.utdallas.sentencebuilder.autocomplete.AutoCompleteService;
import edu.utdallas.sentencebuilder.corpus.ImportService;
import edu.utdallas.sentencebuilder.db.Database;
import edu.utdallas.sentencebuilder.generate.GeneratorFactory;
import edu.utdallas.sentencebuilder.generate.SentenceGenerator;
import edu.utdallas.sentencebuilder.lm.JdbcLanguageModel;
import edu.utdallas.sentencebuilder.lm.LanguageModel;
import edu.utdallas.sentencebuilder.repo.GeneratedSentenceRepository;
import edu.utdallas.sentencebuilder.repo.ImportedFileRepository;
import edu.utdallas.sentencebuilder.repo.WordRepository;

import java.util.List;

/**
 * Wires the services together once and hands them to the controllers.
 * FXML instantiates controllers reflectively, so a small static registry is the
 * simplest way for them to reach shared objects.
 */
public final class AppContext {

    private static AppContext instance;

    public final Database db;
    public final LanguageModel model;
    public final ImportService importService;
    public final WordRepository words;
    public final ImportedFileRepository files;
    public final GeneratedSentenceRepository sentences;
    public final AutoCompleteService autoComplete;
    public final List<SentenceGenerator> generators;

    private AppContext(Database db) {
        this.db = db;
        this.model = new JdbcLanguageModel(db);
        this.importService = new ImportService(db);
        this.words = new WordRepository(db);
        this.files = new ImportedFileRepository(db);
        this.sentences = new GeneratedSentenceRepository(db);
        this.autoComplete = new AutoCompleteService(db, model);
        this.generators = GeneratorFactory.all(model);
    }

    public static void init(Database db) {
        instance = new AppContext(db);
    }

    public static AppContext get() {
        if (instance == null) {
            throw new IllegalStateException("AppContext not initialised");
        }
        return instance;
    }

    public void close() {
        db.close();
    }
}
