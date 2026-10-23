package com.team23.sentencebuilder.generation;

import com.team23.sentencebuilder.logic.Boundary;
import com.team23.sentencebuilder.logic.CorpusCounts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Starts at Boundary.START and picks each next word with probability proportional
 * to how often it followed the current word, until Boundary.END is picked.
 * Stops early at a dead end or at the max length.
 */
public class WeightedRandomGenerator implements SentenceGenerator {
    public static final int DEFAULT_MAX_LENGTH = 20;

    private final CorpusCounts counts;
    private final Random random;
    private final int maxLength;

    public WeightedRandomGenerator(CorpusCounts counts, Random random) {
        this(counts, random, DEFAULT_MAX_LENGTH);
    }

    public WeightedRandomGenerator(CorpusCounts counts, Random random, int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be at least 1");
        }
        this.counts = counts;
        this.random = random;
        this.maxLength = maxLength;
    }

    /** The anchor is ignored. */
    @Override
    public List<String> generate(String anchor) {
        TransitionTable table = new TransitionTable(counts);
        List<String> sentence = new ArrayList<>();
        String current = Boundary.START;

        while (sentence.size() < maxLength) {
            Map<String, Integer> followers = table.followers(current);
            if (followers.isEmpty()) {
                break;
            }

            String next = TransitionTable.pickWeighted(followers, random);
            if (next.equals(Boundary.END)) {
                break;
            }
            sentence.add(next);
            current = next;
        }
        return sentence;
    }
}
