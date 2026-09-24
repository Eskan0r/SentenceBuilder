package edu.utdallas.sentencebuilder.lm;

import edu.utdallas.sentencebuilder.corpus.CorpusCounts;
import edu.utdallas.sentencebuilder.model.Follower;
import edu.utdallas.sentencebuilder.model.WordStats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * {@link LanguageModel} backed by a {@link CorpusCounts} tally.  No database
 * needed, so the generators can be unit-tested and demonstrated quickly.
 */
public final class InMemoryLanguageModel implements LanguageModel {

    private final CorpusCounts counts;
    private final List<String> starters = new ArrayList<>();
    private final List<Integer> starterWeights = new ArrayList<>();
    private long starterTotal;

    public InMemoryLanguageModel(CorpusCounts counts) {
        this.counts = counts;
        counts.words().forEach((w, c) -> {
            if (c.start > 0) {
                starters.add(w);
                starterWeights.add(c.start);
                starterTotal += c.start;
            }
        });
    }

    @Override
    public List<Follower> followers(String word) {
        Map<String, Integer> m = counts.follows().get(word);
        if (m == null) {
            return List.of();
        }
        List<Follower> out = new ArrayList<>(m.size());
        m.forEach((w, n) -> out.add(new Follower(w, n, 0)));
        out.sort(Comparator.comparingInt(Follower::weight).reversed().thenComparing(Follower::word));
        return out;
    }

    @Override
    public Optional<WordStats> stats(String word) {
        CorpusCounts.WordCounts c = counts.words().get(word);
        return c == null ? Optional.empty() : Optional.of(new WordStats(word, c.total, c.start, c.end));
    }

    @Override
    public Optional<String> randomStartWord(Random random) {
        if (starterTotal == 0) {
            return Optional.empty();
        }
        long r = (long) (random.nextDouble() * starterTotal);
        for (int i = 0; i < starters.size(); i++) {
            r -= starterWeights.get(i);
            if (r < 0) {
                return Optional.of(starters.get(i));
            }
        }
        return Optional.of(starters.get(starters.size() - 1));
    }
}
