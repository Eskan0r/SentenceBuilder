package edu.utdallas.sentencebuilder.lm;

import edu.utdallas.sentencebuilder.model.Follower;
import edu.utdallas.sentencebuilder.model.WordStats;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Read-only view of the bigram statistics, which is all a generator or the
 * auto-complete needs.  The production implementation reads MySQL; the tests
 * use an in-memory one built from a {@code CorpusCounts}.
 */
public interface LanguageModel {

    /** Words seen after {@code word}, with counts, sorted by weight descending. Empty if unknown. */
    List<Follower> followers(String word);

    /** Counters for one word, or empty if it has never been seen. */
    Optional<WordStats> stats(String word);

    /**
     * Picks a sentence-starting word at random, each word weighted by how
     * often it has started a sentence.  Empty when the corpus is empty.
     */
    Optional<String> randomStartWord(Random random);

    default boolean knows(String word) {
        return stats(word).isPresent();
    }
}
