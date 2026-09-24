package edu.utdallas.sentencebuilder.generate;

import edu.utdallas.sentencebuilder.lm.LanguageModel;
import edu.utdallas.sentencebuilder.model.Follower;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * The required probabilistic algorithm.  Each follower is chosen with
 * probability proportional to how often it followed the current word, and
 * "end the sentence" competes with the same rule using the current word's
 * end-of-sentence count.  Rare continuations therefore still appear, just
 * rarely, which is exactly how a language model samples its next token.
 */
public final class WeightedRandomGenerator extends SentenceGenerator {

    public WeightedRandomGenerator(LanguageModel model, Random random) {
        super(model, random);
    }

    @Override
    public String name() {
        return "Weighted random";
    }

    @Override
    public String description() {
        return "Picks each next word with probability proportional to how often it followed the current word.";
    }

    @Override
    protected Optional<Follower> chooseNext(String current, List<Follower> followers, int endWeight,
                                            List<String> sentence) {
        List<Integer> weights = new ArrayList<>(followers.size() + 1);
        for (Follower f : followers) {
            weights.add(f.weight());
        }
        weights.add(endWeight);
        int i = pickWeighted(random, weights);
        return i == followers.size() ? Optional.empty() : Optional.of(followers.get(i));
    }
}
