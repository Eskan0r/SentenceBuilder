package com.team23.sentencebuilder.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a sentence into transitions to which an order of two gives us bigrams (one word of
 * context) and an order of three gives us trigrams (two words). Here, sentence pads with
 * start and end markers to help mark where did a sentence begin and end as we flatten this
 * to a large list.
 *
 * @author Alen Jo
 */
public class NGramWindower {
    private static final int MINIMUM_ORDER = 2;
    private final int order;

    /**
     * Creates a windower.
     *
     * @param order - words per window, which includes the predicted word
     * @throws IllegalArgumentException if the order is below 2 (since 2 is the lowest threshold for an n-gram (aka bigrams))
     */
    public NGramWindower(int order) {
        if (order < MINIMUM_ORDER) {
            throw new IllegalArgumentException("order must be at least " + MINIMUM_ORDER);
        }

        this.order = order;
    }

    /**
     * Builds the transitions for a sentence
     *
     * @param sentence - normalized words, in order
     * @return - one transition per word, plus one for the end marker
     */
    public List<Transition> transitions(List<String> sentence)
    {
        List<String> padded = new ArrayList<>();

        // order - 1 start markers so that there's full context for the first word
        for (int ixMarker = 0; ixMarker < order - 1; ixMarker++) {
            padded.add(Boundary.START);
        }
        padded.addAll(sentence);
        padded.add(Boundary.END);

        // window slide across padded sentence with each iteration a predicted word from (order - 1) words before it
        List<Transition> wordTransitions = new ArrayList<>();

        for (int ixNext = order - 1; ixNext < padded.size(); ixNext++) {
            int ixWordContextStart = ixNext - (order - 1);
            List<String> contextWindow = List.copyOf(padded.subList(ixWordContextStart, ixNext));
            wordTransitions.add(new Transition(contextWindow, padded.get(ixNext)));
        }

        return wordTransitions;
    }
}