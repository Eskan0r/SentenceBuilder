package edu.utdallas.sentencebuilder.model;

/** One row of the followers table on the Words screen. */
public record FollowerRow(int wordId, int nextWordId, String nextWord, int count, int chosenCount) {

    public FollowerRow withCount(int newCount) {
        return new FollowerRow(wordId, nextWordId, nextWord, newCount, chosenCount);
    }
}
