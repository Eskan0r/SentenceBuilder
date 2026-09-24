package edu.utdallas.sentencebuilder.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenizerTest {

    @Test
    void splitsSentencesOnTerminators() {
        List<List<String>> s = Tokenizer.sentences("It is a truth universally acknowledged. Is it? Yes!");
        assertEquals(3, s.size());
        assertEquals(List.of("it", "is", "a", "truth", "universally", "acknowledged"), s.get(0));
        assertEquals(List.of("is", "it"), s.get(1));
        assertEquals(List.of("yes"), s.get(2));
    }

    @Test
    void keepsInternalApostrophesAndHyphens() {
        assertEquals(List.of("don't", "mother-in-law", "o'clock"), Tokenizer.words("Don't mother-in-law o'clock"));
        assertEquals(List.of("hello", "world"), Tokenizer.words("'hello' -world-"));
        assertEquals(List.of("it's"), Tokenizer.words("it’s"));   // curly apostrophe
    }

    @Test
    void stripsPunctuationQuotesAndGutenbergUnderscores() {
        assertEquals(List.of("hello", "world", "1815"), Tokenizer.words("\"Hello,\" (world) _1815_;"));
    }

    @Test
    void blankLineEndsASentence() {
        List<List<String>> s = Tokenizer.sentences("CHAPTER I\n\nThe family of Dashwood.");
        assertEquals(List.of(List.of("chapter", "i"), List.of("the", "family", "of", "dashwood")), s);
    }

    @Test
    void trailingTextWithoutPeriodIsStillASentence() {
        assertEquals(List.of(List.of("no", "period")), Tokenizer.sentences("no period"));
    }

    @Test
    void formatsSentence() {
        assertEquals("The end.", Tokenizer.format(List.of("the", "end")));
        assertEquals("", Tokenizer.format(List.of()));
    }
}
