package edu.utdallas.sentencebuilder.generate;

/**
 * Knobs shared by every generator.
 *
 * @param minWords never end a sentence before this many words unless there is no possible next word
 * @param maxWords hard cap so a generator that never picks END still terminates
 */
public record GenerationOptions(int minWords, int maxWords) {

    public static final GenerationOptions DEFAULT = new GenerationOptions(3, 25);

    public GenerationOptions {
        if (minWords < 1 || maxWords < minWords) {
            throw new IllegalArgumentException("need 1 <= minWords <= maxWords");
        }
    }
}
