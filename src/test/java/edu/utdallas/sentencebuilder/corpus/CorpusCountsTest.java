package edu.utdallas.sentencebuilder.corpus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorpusCountsTest {

    @Test
    void countsTotalsStartsEndsAndBigrams() {
        CorpusCounts c = new CorpusCounts();
        c.addSentence(List.of("the", "cat", "sat"));
        c.addSentence(List.of("the", "dog", "sat"));
        c.addSentence(List.of("sat"));

        assertEquals(3, c.sentenceCount());
        assertEquals(7, c.tokenCount());
        assertEquals(4, c.distinctWords());

        CorpusCounts.WordCounts the = c.words().get("the");
        assertEquals(2, the.total);
        assertEquals(2, the.start);
        assertEquals(0, the.end);

        CorpusCounts.WordCounts sat = c.words().get("sat");
        assertEquals(3, sat.total);
        assertEquals(1, sat.start);
        assertEquals(3, sat.end);

        assertEquals(Map.of("cat", 1, "dog", 1), c.follows().get("the"));
        assertEquals(Map.of("sat", 1), c.follows().get("cat"));
        assertEquals(4, c.bigramCount());
    }

    @Test
    void singleWordSentenceIsBothStartAndEnd() {
        CorpusCounts c = new CorpusCounts();
        c.addSentence(List.of("yes"));
        CorpusCounts.WordCounts yes = c.words().get("yes");
        assertEquals(1, yes.start);
        assertEquals(1, yes.end);
        assertEquals(0, c.bigramCount());
    }
}
