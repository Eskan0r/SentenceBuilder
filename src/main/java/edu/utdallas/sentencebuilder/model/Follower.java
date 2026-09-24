package edu.utdallas.sentencebuilder.model;

/**
 * A word that has been seen immediately after some other word.
 *
 * @param word        the following word
 * @param count       times it followed in imported text
 * @param chosenCount times a user chose/typed it after the previous word in auto-complete
 */
public record Follower(String word, int count, int chosenCount) {

    /** Weight used when ranking suggestions: corpus evidence plus user choices. */
    public int weight() {
        return count + chosenCount;
    }
}
