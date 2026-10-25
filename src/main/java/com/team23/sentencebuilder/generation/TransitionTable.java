package com.team23.sentencebuilder.generation;

import com.team23.sentencebuilder.logic.Boundary;
import com.team23.sentencebuilder.logic.CorpusCounts;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Snapshot of the bigram counts with both boundaries kept, indexed forward
 * (word -> next words) and backward (word -> previous words).
 * <p>
 * Public so other features (e.g. autocomplete) can look up followers directly.
 * Returned maps are read-only.
 */
public final class TransitionTable {
    private final Map<String, Map<String, Integer>> forward = new HashMap<>();
    private final Map<String, Map<String, Integer>> backward = new HashMap<>();

    public TransitionTable(CorpusCounts counts) {
        counts.getStarts().forEach((word, count) -> add(Boundary.START, word, count));
        counts.getFollows().forEach((previous, followers) ->
                followers.forEach((next, count) -> add(previous, next, count)));
        counts.getEnds().forEach((word, count) -> add(word, Boundary.END, count));
    }

    /** Words seen after this one, including Boundary.END. Empty for a dead end. */
    public Map<String, Integer> followers(String word) {
        return Collections.unmodifiableMap(forward.getOrDefault(word, Map.of()));
    }

    /** Words seen before this one, including Boundary.START. */
    public Map<String, Integer> predecessors(String word) {
        return Collections.unmodifiableMap(backward.getOrDefault(word, Map.of()));
    }

    /** Whether the word appeared anywhere in the counts. */
    public boolean contains(String word) {
        return forward.containsKey(word) || backward.containsKey(word);
    }

    /**
     * Picks a key with probability proportional to its count.
     *
     * @param options - non-empty map of choices to counts
     */
    public static String pickWeighted(Map<String, Integer> options, Random random) {
        long total = sum(options);
        long roll = random.nextLong(total);

        for (Map.Entry<String, Integer> option : options.entrySet()) {
            roll -= option.getValue();
            if (roll < 0) {
                return option.getKey();
            }
        }
        throw new IllegalStateException("Weights changed during pick");
    }

    public static long sum(Map<String, Integer> counts) {
        long total = 0;
        for (int count : counts.values()) {
            total += count;
        }
        return total;
    }

    private void add(String previous, String next, int count) {
        forward.computeIfAbsent(previous, unused -> new HashMap<>()).merge(next, count, Integer::sum);
        backward.computeIfAbsent(next, unused -> new HashMap<>()).merge(previous, count, Integer::sum);
    }
}
