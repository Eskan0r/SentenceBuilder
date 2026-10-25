package com.team23.sentencebuilder.generation;

import com.team23.sentencebuilder.logic.CorpusCounts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class AnchorWordGeneratorTest {
    private static CorpusCounts sampleCounts() {
        CorpusCounts counts = new CorpusCounts();
        counts.addSentence(List.of("the", "cat", "sat", "on", "the", "mat"));
        counts.addSentence(List.of("the", "dog", "ran"));
        counts.addSentence(List.of("a", "cat", "ran", "home"));
        return counts;
    }

    @Test
    public void growsBothWaysFromTheAnchor() {
        CorpusCounts counts = new CorpusCounts();
        counts.addSentence(List.of("a", "big", "dog", "barked"));

        assertEquals(List.of("a", "big", "dog", "barked"),
                new AnchorWordGenerator(counts, new Random(1)).generate("dog"));
    }

    @Test
    public void anchorIsCaseInsensitive() {
        CorpusCounts counts = new CorpusCounts();
        counts.addSentence(List.of("a", "big", "dog", "barked"));

        assertEquals(List.of("a", "big", "dog", "barked"),
                new AnchorWordGenerator(counts, new Random(1)).generate(" Dog "));
    }

    @Test
    public void containsAnchorAndEveryStepWasSeen() {
        CorpusCounts counts = sampleCounts();
        AnchorWordGenerator generator = new AnchorWordGenerator(counts, new Random(3));
        Map<String, Map<String, Integer>> follows = counts.getFollows();

        for (int run = 0; run < 50; run++) {
            List<String> sentence = generator.generate("cat");
            assertTrue(sentence.contains("cat"));
            assertTrue(counts.getStarts().containsKey(sentence.getFirst()));
            assertTrue(counts.getEnds().containsKey(sentence.getLast()));
            for (int i = 1; i < sentence.size(); i++) {
                assertTrue(follows.get(sentence.get(i - 1)).containsKey(sentence.get(i)));
            }
        }
    }

    @Test
    public void sameSeedGivesSameSentence() {
        CorpusCounts counts = sampleCounts();
        assertEquals(new AnchorWordGenerator(counts, new Random(9)).generate("the"),
                new AnchorWordGenerator(counts, new Random(9)).generate("the"));
    }

    @Test
    public void stopsAtMaxLength() {
        CorpusCounts counts = new CorpusCounts();
        counts.addSentence(List.of("go", "go", "go", "go", "go", "go", "go", "go", "go", "go"));

        for (int seed = 0; seed < 20; seed++) {
            List<String> sentence = new AnchorWordGenerator(counts, new Random(seed), 5).generate("go");
            assertTrue(sentence.size() <= 5);
        }
    }

    @Test
    public void nullAnchorPicksAKnownWord() {
        CorpusCounts counts = sampleCounts();
        List<String> sentence = new AnchorWordGenerator(counts, new Random(5)).generate(null);
        assertFalse(sentence.isEmpty());
        assertTrue(counts.getTotal().keySet().containsAll(sentence));
    }

    @Test
    public void unknownAnchorThrows() {
        AnchorWordGenerator generator = new AnchorWordGenerator(sampleCounts(), new Random(1));
        assertThrows(IllegalArgumentException.class, () -> generator.generate("zebra"));
        assertThrows(IllegalArgumentException.class, () -> generator.generate("<s>"));
    }

    @Test
    public void usesAnchor() {
        assertTrue(new AnchorWordGenerator(new CorpusCounts(), new Random(1)).usesAnchor());
    }
}
