package edu.utdallas.sentencebuilder.generate;

import edu.utdallas.sentencebuilder.lm.LanguageModel;
import edu.utdallas.sentencebuilder.model.Follower;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * "Top-k sampling", the trick real LLMs use to stay coherent: keep only the
 * {@code k} most frequent followers, then sample among those in proportion to
 * their counts.  Rare junk continuations are cut off, but there is still
 * variety between runs.
 */
public final class TopKGenerator extends SentenceGenerator {

    private final int k;

    public TopKGenerator(LanguageModel model, Random random, int k) {
        super(model, random);
        this.k = k;
    }

    @Override
    public String name() {
        return "Top-" + k + " sampling";
    }

    @Override
    public String description() {
        return "Weighted random choice restricted to the " + k + " most common next words (how ChatGPT-style models stay on topic).";
    }

    @Override
    protected Optional<Follower> chooseNext(String current, List<Follower> followers, int endWeight,
                                            List<String> sentence) {
        List<Follower> top = followers.subList(0, Math.min(k, followers.size()));
        List<Integer> weights = new ArrayList<>(top.size() + 1);
        for (Follower f : top) {
            weights.add(f.weight());
        }
        weights.add(endWeight);
        int i = pickWeighted(random, weights);
        return i == top.size() ? Optional.empty() : Optional.of(top.get(i));
    }
}
