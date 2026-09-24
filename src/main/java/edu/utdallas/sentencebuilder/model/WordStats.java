package edu.utdallas.sentencebuilder.model;

/** Per-word counters, as stored in the {@code word} table. */
public record WordStats(String word, int total, int start, int end) {

    /** Fraction of this word's occurrences that ended a sentence. */
    public double endProbability() {
        return total == 0 ? 0 : (double) end / total;
    }
}
