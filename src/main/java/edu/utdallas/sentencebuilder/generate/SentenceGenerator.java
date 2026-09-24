package edu.utdallas.sentencebuilder.generate;

import edu.utdallas.sentencebuilder.lm.LanguageModel;
import edu.utdallas.sentencebuilder.model.Follower;
import edu.utdallas.sentencebuilder.model.GeneratedSentence;
import edu.utdallas.sentencebuilder.model.WordStats;
import edu.utdallas.sentencebuilder.text.Tokenizer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
 * Base class for every sentence-building algorithm (Template Method pattern).
 * <p>
 * {@link #generate} walks the bigram chain: starting from the supplied word it
 * repeatedly asks the subclass to {@link #chooseNext choose} either a follower
 * or "end the sentence here".  Ending is offered to the subclass as a weighted
 * option alongside the followers: the weight is the number of times the current
 * word has ended a sentence, so a word like {@code him} ends sentences far more
 * often than a word like {@code the}.
 * <p>
 * Subclasses only decide <em>which</em> candidate wins; the loop, the length
 * limits and the bookkeeping live here.
 */
public abstract class SentenceGenerator {

    protected final LanguageModel model;
    protected final Random random;

    protected SentenceGenerator(LanguageModel model, Random random) {
        this.model = model;
        this.random = random;
    }

    /** Short name shown in the UI and stored with each generated sentence. */
    public abstract String name();

    /** One-sentence explanation shown as a tooltip. */
    public abstract String description();

    /**
     * Picks the next word.
     *
     * @param current   the word just emitted
     * @param followers candidates seen after {@code current}, sorted by weight descending; never empty
     * @param endWeight how many times {@code current} ended a sentence (0 if ending is not allowed yet)
     * @param sentence  the words emitted so far (read-only), useful for avoiding loops
     * @return the chosen follower, or empty to end the sentence
     */
    protected abstract Optional<Follower> chooseNext(String current, List<Follower> followers,
                                                     int endWeight, List<String> sentence);

    /**
     * Builds one sentence from {@code startWord}.
     *
     * @throws IllegalArgumentException if the start word is not in the corpus
     */
    public GeneratedSentence generate(String startWord, GenerationOptions opts, boolean randomStart) {
        String start = startWord.trim().toLowerCase(Locale.ROOT);
        WordStats startStats = model.stats(start)
                .orElseThrow(() -> new IllegalArgumentException("\"" + startWord + "\" is not in the database"));

        List<String> words = new ArrayList<>();
        words.add(start);
        String current = start;
        int currentEnd = startStats.end();

        while (words.size() < opts.maxWords()) {
            List<Follower> followers = model.followers(current);
            boolean mayEnd = words.size() >= opts.minWords();
            if (followers.isEmpty()) {
                break;                                  // dead end: nothing ever followed this word
            }
            int endWeight = mayEnd ? currentEnd : 0;
            Optional<Follower> next = chooseNext(current, followers, endWeight, List.copyOf(words));
            if (next.isEmpty()) {
                if (mayEnd) {
                    break;
                }
                next = Optional.of(followers.get(0));   // too short to stop: force the top follower
            }
            current = next.get().word();
            words.add(current);
            currentEnd = model.stats(current).map(WordStats::end).orElse(0);
        }

        return new GeneratedSentence(0, Tokenizer.format(words), words, name(), start, randomStart,
                LocalDateTime.now(), null, 0);
    }

    /** Same as {@link #generate(String, GenerationOptions, boolean)} with a random, start-weighted first word. */
    public GeneratedSentence generateFromRandomStart(GenerationOptions opts) {
        String start = model.randomStartWord(random)
                .orElseThrow(() -> new IllegalStateException("The database has no words yet; import a file first."));
        return generate(start, opts, true);
    }

    /** Utility for subclasses: weighted random pick where index {@code weights.size()} means END. */
    protected static int pickWeighted(Random random, List<Integer> weights) {
        long total = 0;
        for (int w : weights) {
            total += w;
        }
        if (total <= 0) {
            return random.nextInt(weights.size());
        }
        long r = (long) (random.nextDouble() * total);
        for (int i = 0; i < weights.size(); i++) {
            r -= weights.get(i);
            if (r < 0) {
                return i;
            }
        }
        return weights.size() - 1;
    }

    @Override
    public String toString() {
        return name();
    }
}
