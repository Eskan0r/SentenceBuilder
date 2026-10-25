package com.team23.sentencebuilder.generation;

import com.team23.sentencebuilder.logic.Boundary;
import com.team23.sentencebuilder.logic.CorpusCounts;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Builds a sentence around a chosen word: grows backward by weighted picks over
 * reverse bigrams until Boundary.START, then forward until Boundary.END.
 * <p>
 * Up to half of the max length goes to the words before the anchor; the forward
 * half gets whatever is left. Either direction stops early at a dead end.
 */
public class AnchorWordGenerator implements SentenceGenerator {
    public static final int DEFAULT_MAX_LENGTH = 20;

    private final CorpusCounts counts;
    private final Random random;
    private final int maxLength;

    public AnchorWordGenerator(CorpusCounts counts, Random random) {
        this(counts, random, DEFAULT_MAX_LENGTH);
    }

    public AnchorWordGenerator(CorpusCounts counts, Random random, int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be at least 1");
        }
        this.counts = counts;
        this.random = random;
        this.maxLength = maxLength;
    }

    /**
     * @param anchor - word to build around (case-insensitive), or null to pick one
     *                 weighted by how often each word occurs
     * @throws IllegalArgumentException if the anchor never appeared in the counts
     */
    @Override
    public List<String> generate(String anchor) {
        TransitionTable table = new TransitionTable(counts);
        String word;

        if (anchor == null) {
            Map<String, Integer> totals = counts.getTotal();
            if (totals.isEmpty()) {
                return List.of();
            }
            word = TransitionTable.pickWeighted(totals, random);
        } else {
            word = anchor.strip().toLowerCase(Locale.ROOT);
            if (word.equals(Boundary.START) || word.equals(Boundary.END) || !table.contains(word)) {
                throw new IllegalArgumentException("Unknown word: " + anchor);
            }
        }

        Deque<String> sentence = new ArrayDeque<>();
        sentence.add(word);

        int backwardLimit = (maxLength - 1) / 2;
        String current = word;
        for (int added = 0; added < backwardLimit; added++) {
            Map<String, Integer> predecessors = table.predecessors(current);
            if (predecessors.isEmpty()) {
                break;
            }

            String previous = TransitionTable.pickWeighted(predecessors, random);
            if (previous.equals(Boundary.START)) {
                break;
            }
            sentence.addFirst(previous);
            current = previous;
        }

        current = word;
        while (sentence.size() < maxLength) {
            Map<String, Integer> followers = table.followers(current);
            if (followers.isEmpty()) {
                break;
            }

            String next = TransitionTable.pickWeighted(followers, random);
            if (next.equals(Boundary.END)) {
                break;
            }
            sentence.addLast(next);
            current = next;
        }
        return new ArrayList<>(sentence);
    }

    @Override
    public boolean usesAnchor() {
        return true;
    }
}
