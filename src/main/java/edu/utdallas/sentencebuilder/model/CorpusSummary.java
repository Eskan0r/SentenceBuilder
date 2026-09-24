package edu.utdallas.sentencebuilder.model;

/** Headline numbers for the Reports screen. */
public record CorpusSummary(
        int distinctWords,
        long totalTokens,
        long sentenceStarts,
        int bigrams,
        int filesImported,
        int sentencesGenerated,
        int distinctSentencesGenerated) {
}
