package com.team23.sentencebuilder.logic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CorpusCounterTest {
    @Test
    public void countWordsStartsEndsAndFollows() {
        CorpusCounts counts = new CorpusCounts();
        counts.addSentence(List.of("the", "cat", "sat"));
        counts.addSentence(List.of("the", "dog", "sat"));

        assertEquals(6, counts.getWordCount());
        assertEquals(2, counts.getTotal().get("the"));
        assertEquals(2, counts.getTotal().get("sat"));
        assertEquals(2, counts.getStarts().get("the"));
        assertEquals(2, counts.getEnds().get("sat"));
        assertEquals(1, counts.getFollows().get("the").get("cat"));
        assertEquals(1, counts.getFollows().get("the").get("dog"));
        assertNull(counts.getFollows().get("sat"));
    }

    @Test
    public void totalEqualsOutgoingCountsForEveryWord() {
        CorpusCounts counts = new CorpusCounts();
        counts.addSentence(List.of("the", "cat", "sat"));
        counts.addSentence(List.of("the", "dog", "sat"));

        Map<String, Map<String, Integer>> follows = counts.getFollows();
        Map<String, Integer> ends = counts.getEnds();

        for (Map.Entry<String, Integer> totalEntry : counts.getTotal().entrySet()) {
            String word = totalEntry.getKey();
            int followerCount = follows.getOrDefault(word, Map.of())
                    .values().stream().mapToInt(Integer::intValue).sum();
            int outgoingCount = followerCount + ends.getOrDefault(word, 0);

            assertEquals(totalEntry.getValue(), outgoingCount, "invariant broken for " + word);
        }
    }

    @Test
    public void boundaryMarkersNeverAppearAsWords() {
        CorpusCounts counts = new CorpusCounts();
        counts.addSentence(List.of("the", "cat"));

        assertFalse(counts.getTotal().containsKey(Boundary.START));
        assertFalse(counts.getTotal().containsKey(Boundary.END));
        assertFalse(counts.getFollows().containsKey(Boundary.START));
        assertFalse(counts.getFollows().get("the").containsKey(Boundary.END));
        assertFalse(counts.getEnds().containsKey(Boundary.START));
    }

    @Test
    public void emptySentenceIsIgnored() {
        CorpusCounts counts = new CorpusCounts();
        counts.addSentence(List.of());

        assertEquals(0, counts.getWordCount());
        assertEquals(0, counts.getTotal().size());
    }

    @Test
    public void incrementalTransitionsMatchAddSentence() {
        CorpusCounts incremental = new CorpusCounts();
        incremental.addTransition(Boundary.START, "the");
        incremental.addTransition("the", "cat");
        incremental.addTransition("cat", Boundary.END);

        CorpusCounts whole = new CorpusCounts();
        whole.addSentence(List.of("the", "cat"));

        assertEquals(whole.getTotal(), incremental.getTotal());
        assertEquals(whole.getStarts(), incremental.getStarts());
        assertEquals(whole.getEnds(), incremental.getEnds());
        assertEquals(whole.getFollows(), incremental.getFollows());
        assertEquals(whole.getWordCount(), incremental.getWordCount());
    }

    @Test
    public void addFollowIncrementsKnownPairs() {
        CorpusCounts counts = new CorpusCounts();
        counts.addFollow("the", "cat");
        counts.addFollow("the", "cat");

        assertEquals(2, counts.getFollows().get("the").get("cat"));
        assertEquals(2, counts.getTotal().get("cat"));
    }

    @Test
    public void mergeAddsCountsTogether() {
        CorpusCounts first = new CorpusCounts();
        CorpusCounts second = new CorpusCounts();
        first.addSentence(List.of("a", "b"));
        second.addSentence(List.of("a", "b"));

        first.merge(second);

        assertEquals(2, first.getFollows().get("a").get("b"));
        assertEquals(2, first.getStarts().get("a"));
        assertEquals(4, first.getWordCount());
    }
}