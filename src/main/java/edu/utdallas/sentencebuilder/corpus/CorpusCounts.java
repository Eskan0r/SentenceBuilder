package edu.utdallas.sentencebuilder.corpus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory tally of everything the database needs to know about a body of
 * text: per-word totals and the bigram (word, next word) counts.  The importer
 * fills one of these while streaming a file and then merges it into MySQL in a
 * handful of batched statements, which is orders of magnitude faster than one
 * UPDATE per token.
 */
public final class CorpusCounts {

    /** Mutable counters for one word. */
    public static final class WordCounts {
        public int total;
        public int start;
        public int end;
    }

    private final Map<String, WordCounts> words = new HashMap<>();
    private final Map<String, Map<String, Integer>> follows = new HashMap<>();
    private long tokenCount;
    private long sentenceCount;

    public void addSentence(List<String> sentence) {
        if (sentence.isEmpty()) {
            return;
        }
        sentenceCount++;
        String prev = null;
        for (String w : sentence) {
            WordCounts wc = words.computeIfAbsent(w, k -> new WordCounts());
            wc.total++;
            tokenCount++;
            if (prev == null) {
                wc.start++;
            } else {
                follows.computeIfAbsent(prev, k -> new HashMap<>()).merge(w, 1, Integer::sum);
            }
            prev = w;
        }
        words.get(prev).end++;
    }

    public Map<String, WordCounts> words() {
        return words;
    }

    public Map<String, Map<String, Integer>> follows() {
        return follows;
    }

    public long tokenCount() {
        return tokenCount;
    }

    public long sentenceCount() {
        return sentenceCount;
    }

    public int distinctWords() {
        return words.size();
    }

    public int bigramCount() {
        return follows.values().stream().mapToInt(Map::size).sum();
    }
}
