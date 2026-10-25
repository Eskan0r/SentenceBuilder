package com.team23.sentencebuilder.generation;

import com.team23.sentencebuilder.logic.Boundary;
import com.team23.sentencebuilder.logic.CorpusCounts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Beam search: keeps the top-k partial sentences by total log-probability and
 * returns the finished sentence with the highest overall probability.
 * <p>
 * Beam search alone is deterministic, so the first word is sampled by weight
 * (using the injected Random) and the search finds the most likely sentence
 * that opens with it. A dead end or the max length finishes a sentence where it is.
 * <p>
 * Scores are not length-normalized, so shorter sentences are favored.
 */
public class BeamSearchGenerator implements SentenceGenerator {
    public static final int DEFAULT_BEAM_WIDTH = 5;
    public static final int DEFAULT_MAX_LENGTH = 20;

    private final CorpusCounts counts;
    private final Random random;
    private final int beamWidth;
    private final int maxLength;

    private record Beam(List<String> words, double logProbability) {
    }

    public BeamSearchGenerator(CorpusCounts counts, Random random) {
        this(counts, random, DEFAULT_BEAM_WIDTH, DEFAULT_MAX_LENGTH);
    }

    public BeamSearchGenerator(CorpusCounts counts, Random random, int beamWidth, int maxLength) {
        if (beamWidth < 1 || maxLength < 1) {
            throw new IllegalArgumentException("beamWidth and maxLength must be at least 1");
        }
        this.counts = counts;
        this.random = random;
        this.beamWidth = beamWidth;
        this.maxLength = maxLength;
    }

    /** The anchor is ignored. */
    @Override
    public List<String> generate(String anchor) {
        TransitionTable table = new TransitionTable(counts);
        Map<String, Integer> starts = table.followers(Boundary.START);
        if (starts.isEmpty()) {
            return List.of();
        }

        String first = TransitionTable.pickWeighted(starts, random);
        if (first.equals(Boundary.END)) {
            return List.of();
        }
        double firstLogProbability = Math.log((double) starts.get(first) / TransitionTable.sum(starts));

        List<Beam> open = List.of(new Beam(List.of(first), firstLogProbability));
        Beam best = null;

        while (!open.isEmpty()) {
            List<Beam> candidates = new ArrayList<>();

            for (Beam beam : open) {
                Map<String, Integer> followers = table.followers(beam.words().getLast());
                if (followers.isEmpty() || beam.words().size() >= maxLength) {
                    best = better(best, beam);
                    continue;
                }

                long total = TransitionTable.sum(followers);
                for (Map.Entry<String, Integer> follower : followers.entrySet()) {
                    double logProbability = beam.logProbability()
                            + Math.log((double) follower.getValue() / total);

                    if (follower.getKey().equals(Boundary.END)) {
                        best = better(best, new Beam(beam.words(), logProbability));
                    } else {
                        List<String> words = new ArrayList<>(beam.words());
                        words.add(follower.getKey());
                        candidates.add(new Beam(words, logProbability));
                    }
                }
            }

            // Log-probabilities only go down, so anything already worse than best can be dropped.
            double cutoff = best == null ? Double.NEGATIVE_INFINITY : best.logProbability();
            open = candidates.stream()
                    .filter(candidate -> candidate.logProbability() > cutoff)
                    .sorted(Comparator.comparingDouble(Beam::logProbability).reversed())
                    .limit(beamWidth)
                    .toList();
        }
        return best.words();
    }

    private static Beam better(Beam current, Beam candidate) {
        if (current == null || candidate.logProbability() > current.logProbability()) {
            return candidate;
        }
        return current;
    }
}
