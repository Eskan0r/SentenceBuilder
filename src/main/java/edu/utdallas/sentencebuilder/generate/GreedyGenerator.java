package edu.utdallas.sentencebuilder.generate;

import edu.utdallas.sentencebuilder.lm.LanguageModel;
import edu.utdallas.sentencebuilder.model.Follower;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Always takes the most frequent follower, so the same start word always
 * produces the same sentence.  To stop it looping forever on
 * "of the ... of the ..." it refuses to reuse a bigram already in the
 * sentence and falls back to the next most frequent one.  Ends when the
 * end-of-sentence count beats every remaining follower.
 */
public final class GreedyGenerator extends SentenceGenerator {

    public GreedyGenerator(LanguageModel model, Random random) {
        super(model, random);
    }

    @Override
    public String name() {
        return "Most frequent";
    }

    @Override
    public String description() {
        return "Always takes the most common next word (deterministic); never repeats a word pair.";
    }

    @Override
    protected Optional<Follower> chooseNext(String current, List<Follower> followers, int endWeight,
                                            List<String> sentence) {
        Set<String> used = new HashSet<>();
        for (int i = 0; i + 1 < sentence.size(); i++) {
            used.add(sentence.get(i) + " " + sentence.get(i + 1));
        }
        for (Follower f : followers) {              // already sorted by weight descending
            if (endWeight > f.weight()) {
                return Optional.empty();
            }
            if (!used.contains(current + " " + f.word())) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }
}
