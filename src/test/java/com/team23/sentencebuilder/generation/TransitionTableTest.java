package com.team23.sentencebuilder.generation;

import com.team23.sentencebuilder.logic.Boundary;
import com.team23.sentencebuilder.logic.CorpusCounts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class TransitionTableTest {
    @Test
    public void keepsBothBoundariesInBothDirections() {
        CorpusCounts counts = new CorpusCounts();
        counts.addSentence(List.of("the", "cat", "sat"));
        counts.addSentence(List.of("the", "dog"));
        TransitionTable table = new TransitionTable(counts);

        assertEquals(Map.of("the", 2), table.followers(Boundary.START));
        assertEquals(Map.of(Boundary.END, 1), table.followers("sat"));
        assertEquals(Map.of(Boundary.START, 2), table.predecessors("the"));
        assertEquals(Map.of("cat", 1, "dog", 1), table.followers("the"));
        assertEquals(Map.of("sat", 1, "dog", 1), table.predecessors(Boundary.END));
        assertTrue(table.contains("dog"));
        assertFalse(table.contains("bird"));
        assertTrue(table.followers("bird").isEmpty());
    }

    @Test
    public void pickWeightedOnlyReturnsOptionsWithCounts() {
        Map<String, Integer> options = Map.of("a", 3, "b", 1);
        Random random = new Random(1);
        int aPicks = 0;

        for (int i = 0; i < 1000; i++) {
            String pick = TransitionTable.pickWeighted(options, random);
            assertTrue(options.containsKey(pick));
            if (pick.equals("a")) {
                aPicks++;
            }
        }
        assertTrue(aPicks > 650 && aPicks < 850, "a picked " + aPicks + " times");
    }
}
