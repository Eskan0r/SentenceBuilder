package edu.utdallas.sentencebuilder.generate;

import edu.utdallas.sentencebuilder.lm.LanguageModel;

import java.util.List;
import java.util.Random;

/** The single place that knows which generators exist; the UI just asks for the list. */
public final class GeneratorFactory {

    private GeneratorFactory() {
    }

    public static List<SentenceGenerator> all(LanguageModel model) {
        Random random = new Random();
        return List.of(
                new WeightedRandomGenerator(model, random),
                new TopKGenerator(model, random, 5),
                new UniformRandomGenerator(model, random),
                new GreedyGenerator(model, random));
    }
}
