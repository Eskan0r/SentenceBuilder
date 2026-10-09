package com.team23.sentencebuilder.logic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NGramWindowerTest {
    @Test
    public void bigramWindowIncludesBoundaries() {
        List<Transition> transitions = new NGramWindower(2).transitions(List.of("the", "cat"));

        assertEquals(3, transitions.size());
        assertEquals(new Transition(List.of("<s>"), "the"), transitions.get(0));
        assertEquals(new Transition(List.of("the"), "cat"), transitions.get(1));
        assertEquals(new Transition(List.of("cat"), "</s>"), transitions.get(2));
    }

    @Test
    public void trigramPadsTwoStartMarkers() {
        List<Transition> transitions = new NGramWindower(3).transitions(List.of("the", "cat"));

        assertEquals(new Transition(List.of("<s>", "<s>"), "the"), transitions.get(0));
        assertEquals(new Transition(List.of("<s>", "the"), "cat"), transitions.get(1));
        assertEquals(new Transition(List.of("the", "cat"), "</s>"), transitions.get(2));
    }

    @Test
    public void oneWordSentenceStillHasStartAndEnd() {
        List<Transition> transitions = new NGramWindower(2).transitions(List.of("yes"));

        assertEquals(2, transitions.size());
        assertEquals(new Transition(List.of("<s>"), "yes"), transitions.get(0));
        assertEquals(new Transition(List.of("yes"), "</s>"), transitions.get(1));
    }

    @Test
    public void orderBelowTwoIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new NGramWindower(1));
    }
}
