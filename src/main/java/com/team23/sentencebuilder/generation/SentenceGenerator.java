package com.team23.sentencebuilder.generation;

import java.util.List;

/**
 * Builds a sentence from learned word counts. Implementations are interchangeable
 * so the UI can switch algorithms.
 */
public interface SentenceGenerator {

    /**
     * Generates one sentence.
     *
     * @param anchor - a word the sentence should contain, or null for none.
     *                 Generators that don't use anchors ignore it.
     * @return - the sentence's words in order, without boundary markers; empty if
     *           nothing has been learned yet
     */
    List<String> generate(String anchor);

    /**
     * @return - true if this generator builds its sentence around the anchor word
     */
    default boolean usesAnchor() {
        return false;
    }
}
