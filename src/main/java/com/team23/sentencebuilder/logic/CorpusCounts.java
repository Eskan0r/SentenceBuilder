package com.team23.sentencebuilder.logic;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * An in-memory bigram counter which then has things stored as transitions:
 * previous word -> next word -> count. Boundary.START and Boundary.END stand
 * in for sentence starts and ends, so start counts, end counts, and follow counts
 * are all the same kind of fact. That also guarantees that every word's total
 * count equals the sum of its outgoing counts.
 * <p>
 * The inspiration behind the CorpusCounts came from: <a href="https://web.stanford.edu/~jurafsky/slp3/">Speech and Language Processing</a>
 * (Jurafsky and Martin) where like the NGramWindower algorithm, it also mentioned on how to actually
 * count the words and write out the probability distributions for this.
 */
public class CorpusCounts {
    private static final int BIGRAM_ORDER = 2;
    private final NGramWindower ngramWindower = new NGramWindower(BIGRAM_ORDER);

    // previous word (or START) -> next word (or END) -> times seen
    private final Map<String, Map<String, Integer>> transitionCounts = new HashMap<>();

    // real word -> times it occurred (boundary markers not counted!)
    private final Map<String, Integer> totalCounts = new HashMap<>();

    private int wordCount;

    /**
     * Counts one whole text (whether it'd be imported, generated, or typed) and ignores
     * empty sentences
     *
     * @param words - normalized words of a sentence in order
     */
    public void addSentence(List<String> words) {
        if (!words.isEmpty()) {
            for (Transition wordTransition : ngramWindower.transitions(words)) {
                addCount(wordTransition.context().getFirst(), wordTransition.next());
            }
        }
    }

    /**
     * Counts a single step for the autocomplete as user types and uses
     * Boundary.START as previousWord for the first word in a sentence and
     * Boundary.END as nextWord when sentence ends.
     *
     * @param previousWord - the word before, or Boundary.START
     * @param nextWord - the word after, or Boundary.END
     */
    public void addTransition(String previousWord, String nextWord) {
        addCount(previousWord, nextWord);
    }

    /**
     * Counts that the user chose nextWord right after previousWord, which
     * is same as {@code addTransition};
     *
     * @param previousWord - the word before
     * @param nextWord - word chosen or typed
     */
    public void addFollow(String previousWord, String nextWord) {
        addTransition(previousWord, nextWord);
    }

    /**
     * Adds every count from another set of counts into this one
     *
     * @param other - counts to add
     */
    public void merge(CorpusCounts other) {
        for (Map.Entry<String, Map<String, Integer>> previousEntry : other.transitionCounts.entrySet()) {
            for (Map.Entry<String, Integer> nextEntry : previousEntry.getValue().entrySet()) {
                addCount(previousEntry.getKey(), nextEntry.getKey(), nextEntry.getValue());
            }
        }
    }

    /**
     * Retrieves total word occurrences as a read-only map
     *
     * @return - how many times each word occurred in total (read-only view)
     */
    public Map<String, Integer> getTotal() {
        return Collections.unmodifiableMap(totalCounts);
    }

    /**
     * Retrieves all start word occurrences from a sentence as a read-only map
     *
     * @return - how many times each start word of a sentence occurred (read-only view)
     */
    public Map<String, Integer> getStarts() {
        Map<String, Integer> starts = transitionCounts.getOrDefault(Boundary.START, Map.of());
        return Collections.unmodifiableMap(starts);
    }

    /**
     * Retrieves all end word occurrences from a sentence and is re-computed on
     * each call.
     *
     * @return - how many times each word ended a sentence
     */
    public Map<String, Integer> getEnds() {
        Map<String, Integer> ends = new HashMap<>();

        for (Map.Entry<String, Map<String, Integer>> previousEntry : transitionCounts.entrySet()) {
            Integer endCount = previousEntry.getValue().get(Boundary.END);
            if (!previousEntry.getKey().equals(Boundary.START) && endCount != null) {
                ends.put(previousEntry.getKey(), endCount);
            }
        }
        return ends;
    }

    /**
     * Retrieves all word follow counts in a sentence
     *
     * @return - for each word, the words that followed it and how often
     */
    public Map<String, Map<String, Integer>> getFollows() {
        Map<String, Map<String, Integer>> follows = new HashMap<>();

        for (Map.Entry<String, Map<String, Integer>> previousEntry : transitionCounts.entrySet()) {
            Map<String, Integer> realFollowers = new HashMap<>(previousEntry.getValue());
            realFollowers.remove(Boundary.END);

            if (!previousEntry.getKey().equals(Boundary.START) && !realFollowers.isEmpty()) {
                follows.put(previousEntry.getKey(), realFollowers);
            }
        }
        return follows;
    }

    /**
     * total count of words for a text corpus
     *
     * @return - total count of words
     */
    public int getWordCount() {
        return wordCount;
    }

    /**
     * Records occurrences of a mapping: previousWord -> nextWord. It only counts
     * real nextWords in sequence as a single occurrence and strips away END markers
     * from getting counted as an occurrence.
     *
     * @param previousWord - current words predecessor or Boundary.START
     * @param nextWord     - current words successor or Boundary.END
     */
    private void addCount(String previousWord, String nextWord)
    {
        transitionCounts.computeIfAbsent(previousWord, k -> new HashMap<>())
                .merge(nextWord, 1, Integer::sum);

        if (!nextWord.equals(Boundary.END)) {
            totalCounts.merge(nextWord, 1, Integer::sum);
            wordCount++;
        }
    }

    /**
     * Records occurrences of a mapping: previousWord -> nextWord. It only counts
     * real nextWords in sequence as a single occurrence and strips away END markers
     * from getting counted as an occurrence.
     *
     * @param previousWord - current words predecessor or Boundary.START
     * @param nextWord - current words successor or Boundary.END
     * @param occurrences - occurrences of the nextWord
     */
    private void addCount(String previousWord, String nextWord, int occurrences) {
        transitionCounts.computeIfAbsent(previousWord, k -> new HashMap<>())
                .merge(nextWord, occurrences, Integer::sum);

        if (!nextWord.equals(Boundary.END)) {
            totalCounts.merge(nextWord, occurrences, Integer::sum);
            wordCount += occurrences;
        }
    }
}
