package com.team23.sentencebuilder.generation;

import com.team23.sentencebuilder.logic.Boundary;
import com.team23.sentencebuilder.logic.CorpusCounts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class BeamSearchGeneratorTest {
    @Test
    public void returnsMostLikelySentence() {
        CorpusCounts counts = new CorpusCounts();
        for (int i = 0; i < 3; i++) {
            counts.addSentence(List.of("the", "cat", "sat", "down"));
        }
        counts.addSentence(List.of("the", "dog", "ran", "away"));

        for (int seed = 0; seed < 10; seed++) {
            assertEquals(List.of("the", "cat", "sat", "down"),
                    new BeamSearchGenerator(counts, new Random(seed)).generate(null));
        }
    }

    @Test
    public void findsPathThatGreedyWouldMiss() {
        // Greedy takes "a" -> "b" (3 vs 2), but b has 3 risky continuations while c always ends.
        CorpusCounts counts = new CorpusCounts();
        counts.addSentence(List.of("x", "a", "b", "p"));
        counts.addSentence(List.of("x", "a", "b", "q"));
        counts.addSentence(List.of("x", "a", "b", "r"));
        counts.addSentence(List.of("x", "a", "c"));
        counts.addSentence(List.of("x", "a", "c"));

        assertEquals(List.of("x", "a", "c"), new BeamSearchGenerator(counts, new Random(1)).generate(null));
    }

    @Test
    public void stopsAtMaxLength() {
        CorpusCounts counts = new CorpusCounts();
        counts.addSentence(List.of("go", "go", "go", "go", "go", "go", "go", "go", "go", "go"));

        List<String> sentence = new BeamSearchGenerator(counts, new Random(1), 3, 4).generate(null);
        assertTrue(sentence.size() <= 4);
        assertFalse(sentence.isEmpty());
    }

    @Test
    public void stopsAtDeadEnd() {
        CorpusCounts counts = new CorpusCounts();
        counts.addTransition(Boundary.START, "hello");

        assertEquals(List.of("hello"), new BeamSearchGenerator(counts, new Random(1)).generate(null));
    }

    @Test
    public void emptyCountsGiveEmptySentence() {
        assertEquals(List.of(), new BeamSearchGenerator(new CorpusCounts(), new Random(1)).generate(null));
    }

    @Test
    public void rejectsBadSettings() {
        CorpusCounts counts = new CorpusCounts();
        assertThrows(IllegalArgumentException.class, () -> new BeamSearchGenerator(counts, new Random(1), 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new BeamSearchGenerator(counts, new Random(1), 3, 0));
    }
}
