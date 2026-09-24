package edu.utdallas.sentencebuilder.generate;

import edu.utdallas.sentencebuilder.corpus.CorpusCounts;
import edu.utdallas.sentencebuilder.lm.InMemoryLanguageModel;
import edu.utdallas.sentencebuilder.lm.LanguageModel;
import edu.utdallas.sentencebuilder.model.GeneratedSentence;
import edu.utdallas.sentencebuilder.text.Tokenizer;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorTest {

    private static final String CORPUS = """
            The cat sat on the mat. The cat ate the rat. The dog sat on the log.
            A dog ate the cat. The rat ran. The mat is red. The cat is black.
            """;

    private static LanguageModel model() {
        CorpusCounts counts = new CorpusCounts();
        Tokenizer.sentences(CORPUS).forEach(counts::addSentence);
        return new InMemoryLanguageModel(counts);
    }

    @Test
    void everyGeneratedWordFollowsThePreviousOneInTheCorpus() {
        LanguageModel m = model();
        for (SentenceGenerator g : GeneratorFactory.all(m)) {
            for (int i = 0; i < 50; i++) {
                GeneratedSentence s = g.generate("the", GenerationOptions.DEFAULT, false);
                List<String> w = s.words();
                assertEquals("the", w.get(0));
                assertTrue(w.size() <= GenerationOptions.DEFAULT.maxWords(), g.name() + " exceeded max words");
                for (int j = 1; j < w.size(); j++) {
                    String prev = w.get(j - 1);
                    String next = w.get(j);
                    assertTrue(m.followers(prev).stream().anyMatch(f -> f.word().equals(next)),
                            g.name() + ": \"" + next + "\" never followed \"" + prev + "\"");
                }
            }
        }
    }

    @Test
    void greedyIsDeterministic() {
        LanguageModel m = model();
        GreedyGenerator g = new GreedyGenerator(m, new Random(1));
        String a = g.generate("the", GenerationOptions.DEFAULT, false).text();
        String b = g.generate("the", GenerationOptions.DEFAULT, false).text();
        assertEquals(a, b);
    }

    @Test
    void weightedRandomProducesVariety() {
        LanguageModel m = model();
        WeightedRandomGenerator g = new WeightedRandomGenerator(m, new Random(42));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(g.generate("the", GenerationOptions.DEFAULT, false).text());
        }
        assertTrue(seen.size() > 3, "expected several different sentences, got " + seen);
    }

    @Test
    void respectsMinWordsWhenPossible() {
        LanguageModel m = model();
        GenerationOptions opts = new GenerationOptions(4, 10);
        UniformRandomGenerator g = new UniformRandomGenerator(m, new Random(7));
        for (int i = 0; i < 50; i++) {
            GeneratedSentence s = g.generate("the", opts, false);
            // "ran" and "red"/"black" are dead ends, so shorter sentences are only allowed via dead ends.
            String last = s.words().get(s.words().size() - 1);
            assertTrue(s.words().size() >= 4 || m.followers(last).isEmpty(), s.text());
        }
    }

    @Test
    void randomStartWordIsASentenceStarter() {
        LanguageModel m = model();
        Random r = new Random(3);
        for (int i = 0; i < 50; i++) {
            String w = m.randomStartWord(r).orElseThrow();
            assertTrue(m.stats(w).orElseThrow().start() > 0, w);
        }
    }

    @Test
    void unknownStartWordIsRejected() {
        WeightedRandomGenerator g = new WeightedRandomGenerator(model(), new Random());
        assertThrows(IllegalArgumentException.class, () -> g.generate("zebra", GenerationOptions.DEFAULT, false));
    }

    @Test
    void sentenceTextIsCapitalisedAndTerminated() {
        GeneratedSentence s = new GreedyGenerator(model(), new Random()).generate("the", GenerationOptions.DEFAULT, false);
        assertTrue(s.text().startsWith("The "));
        assertTrue(s.text().endsWith("."));
    }
}
