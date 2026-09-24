package edu.utdallas.sentencebuilder.generate;

import edu.utdallas.sentencebuilder.lm.LanguageModel;
import edu.utdallas.sentencebuilder.model.Follower;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Ignores the counts entirely: every word that has ever followed the current
 * word is equally likely, and ending is one more equally-likely option (when
 * the current word has ever ended a sentence).  Produces the strangest text,
 * which is a nice contrast with the weighted algorithm.
 */
public final class UniformRandomGenerator extends SentenceGenerator {

    public UniformRandomGenerator(LanguageModel model, Random random) {
        super(model, random);
    }

    @Override
    public String name() {
        return "Uniform random";
    }

    @Override
    public String description() {
        return "Every word that has ever followed the current word is equally likely, ignoring frequency.";
    }

    @Override
    protected Optional<Follower> chooseNext(String current, List<Follower> followers, int endWeight,
                                            List<String> sentence) {
        int options = followers.size() + (endWeight > 0 ? 1 : 0);
        int i = random.nextInt(options);
        return i == followers.size() ? Optional.empty() : Optional.of(followers.get(i));
    }
}
