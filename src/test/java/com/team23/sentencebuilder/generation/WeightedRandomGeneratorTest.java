package com.team23.sentencebuilder.generation;

import com.team23.sentencebuilder.logic.Boundary;
import com.team23.sentencebuilder.logic.CorpusCounts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class WeightedRandomGeneratorTest {
    private static CorpusCounts sampleCounts() {
        CorpusCounts counts = new CorpusCounts();
        counts.addSentence(List.of("the", "cat", "sat", "on", "the", "mat"));
        counts.addSentence(List.of("the", "dog", "ran"));
        counts.addSentence(List.of("a", "cat", "ran", "home"));
        return counts;
    }

    @Test
    public void sameSeedGivesSameSentence() {
        CorpusCounts counts = sampleCounts();
        List<String> first = new WeightedRandomGenerator(counts, new Random(42)).generate(null);
        List<String> second = new WeightedRandomGenerator(counts, new Random(42)).generate(null);
        assertEquals(first, second);
    }

    @Test
    public void everyStepWasSeenInTheCounts() {
        CorpusCounts counts = sampleCounts();
        WeightedRandomGenerator generator = new WeightedRandomGenerator(counts, new Random(7));
        Map<String, Map<String, Integer>> follows = counts.getFollows();

        for (int run = 0; run < 50; run++) {
            List<String> sentence = generator.generate(null);
            assertFalse(sentence.isEmpty());
            assertTrue(counts.getStarts().containsKey(sentence.getFirst()));
            assertTrue(counts.getEnds().containsKey(sentence.getLast()));
            for (int i = 1; i < sentence.size(); i++) {
                assertTrue(follows.get(sentence.get(i - 1)).containsKey(sentence.get(i)));
            }
        }
    }

    @Test
    public void stopsAtMaxLength() {
        CorpusCounts counts = new CorpusCounts();
        counts.addSentence(List.of("go", "go", "go", "go", "go", "go", "go", "go", "go", "go"));

        for (int seed = 0; seed < 20; seed++) {
            List<String> sentence = new WeightedRandomGenerator(counts, new Random(seed), 4).generate(null);
            assertTrue(sentence.size() <= 4);
        }
    }

    @Test
    public void stopsAtDeadEnd() {
        CorpusCounts counts = new CorpusCounts();
        counts.addTransition(Boundary.START, "hello");

        assertEquals(List.of("hello"), new WeightedRandomGenerator(counts, new Random(1)).generate(null));
    }

    @Test
    public void emptyCountsGiveEmptySentence() {
        assertEquals(List.of(), new WeightedRandomGenerator(new CorpusCounts(), new Random(1)).generate(null));
    }
}
