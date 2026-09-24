package edu.utdallas.sentencebuilder.model;

/** One row of the Words screen: the {@code word} table plus how many distinct followers it has. */
public record WordRow(int id, String text, int total, int start, int end, int followerCount) {

    public WordRow withCounts(int newTotal, int newStart, int newEnd) {
        return new WordRow(id, text, newTotal, newStart, newEnd, followerCount);
    }
}
