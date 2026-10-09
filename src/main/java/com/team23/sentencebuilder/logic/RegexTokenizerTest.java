package com.team23.sentencebuilder.logic;

import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class RegexTokenizerTest {
    private final Tokenizer tokenizer = new RegexTokenizer();

    // sentence operation tests

    @Test
    public void splitsSentencesAndHandlesAbbreviations()
    {
        var tokenized = tokenizer.tokenize("Prof. Cole meets us Tuesdays 1:15 PM. Must be there.");
        assertEquals(2, tokenized.size());
        assertEquals(List.of("prof", "cole", "meets", "us", "tuesdays", "1:15", "pm"), tokenized.get(0));
        assertEquals(List.of("must", "be", "there"), tokenized.get(1));
    }

    @Test
    public void abbrieviationMatchIsCaseInsensitive()
    {
        var tokenized = tokenizer.tokenize("Prof. Cole met us.");
        assertEquals(List.of(List.of("prof", "cole", "met", "us")), tokenized);
    }

    @Test
    public void collapsesRepeatedEndPunctuation()
    {
        var tokenized = tokenizer.tokenize("Uhhh...that idea is interesting?!");
        assertEquals(List.of(List.of("uhhh"), List.of("that", "idea", "is", "interesting")), tokenized);
    }

    @Test
    public void lastSentenceWithoutEndPunctuationKept()
    {
        var tokenized = tokenizer.tokenize("no period here");
        assertEquals(List.of(List.of("no", "period", "here")), tokenized);
    }

    @Test
    public void sentenceCanSpanHardWrappedLines()
    {
        var tokenized = tokenizer.tokenize("line one\nline two");
        assertEquals(List.of(List.of("line", "one", "line", "two")), tokenized);
    }

    @Test
    public void decimalsAndGroupedNumbersStayWhole() {
        assertEquals(List.of("it", "costs", "3.5", "or", "1,000"),
                tokenizer.tokenize("It costs 3.5 or 1,000.").get(0));
    }

    @Test
    public void sentenceFinalNumberStillEndsSentence() {
        assertEquals(List.of(List.of("in", "1815"), List.of("he", "left")),
                tokenizer.tokenize("In 1815. He left."));
    }

    // paragraph breaks (and Gutenberg Headings)

    @Test
    public void blankLineEndsASentence() {
        var result = tokenizer.tokenize("CHAPTER I\n\nIt was late.");
        assertEquals(List.of(List.of("chapter", "i"), List.of("it", "was", "late")), result);
    }

    @Test
    public void windowsLineEndingsAlsoCountAsParagraphBreak() {
        var result = tokenizer.tokenize("CHAPTER I\r\n\r\nIt was late.");
        assertEquals(2, result.size());
    }

    // definition of a word

    @Test
    public void keepsContractionsAndHyphens() {
        var result = tokenizer.tokenize("It's a well-known fact.");
        assertEquals(List.of("it's", "a", "well-known", "fact"), result.get(0));
    }

    @Test
    public void normalizesCurlyApostrophes() {
        var result = tokenizer.tokenize("Don\u2019t go.");
        assertEquals(List.of("don't", "go"), result.getFirst());
    }

    @Test
    public void lowercasesEverything() {
        var result = tokenizer.tokenize("The the THE.");
        assertEquals(List.of("the", "the", "the"), result.getFirst());
    }

    @Test
    public void numbersCountAsWords() {
        var result = tokenizer.tokenize("In 1815 he left.");
        assertEquals(List.of("in", "1815", "he", "left"), result.get(0));
    }

    @Test
    public void acceptsAccentedLetters() {
        var result = tokenizer.tokenize("Caf\u00e9 au lait.");
        assertEquals(List.of("caf\u00e9", "au", "lait"), result.get(0));
    }

    @Test
    public void trailingApostropheIsDropped() {
        // todo: known limitation and a fix will be incoming
        var result = tokenizer.tokenize("The dogs' bones.");
        assertEquals(List.of("the", "dogs", "bones"), result.getFirst());
    }

    // discarding punctuation

    @Test
    public void dropsCommasAndQuotes() {
        var result = tokenizer.tokenize("\"Yes,\" she said.");
        assertEquals(List.of("yes", "she", "said"), result.get(0));
    }

    @Test
    public void doubleHyphenSeparatesWords() {
        var result = tokenizer.tokenize("one--two.");
        assertEquals(List.of("one", "two"), result.get(0));
    }

    @Test
    public void ignoresUnderscoresFromGutenbergItalics() {
        var result = tokenizer.tokenize("_italics_ word.");
        assertEquals(List.of("italics", "word"), result.get(0));
    }

    // empty input

    @Test
    public void emptyAndPunctuationOnlyInputGiveNoSentences() {
        assertTrue(tokenizer.tokenize("").isEmpty());
        assertTrue(tokenizer.tokenize("... , \" !").isEmpty());
    }

    // normalize()

    @Test
    public void normalizeMatchesTokenizeOutput() {
        String stored = tokenizer.tokenize("Don\u2019t.").getFirst().getFirst();
        assertEquals(stored, tokenizer.normalize("DON\u2019T"));
    }

    // configurable abbreviations
    @Test
    public void defaultAbbreviationsDoNotIncludeEtc() {
        assertEquals(2, tokenizer.tokenize("Apples etc. are fine.").size());
    }

    @Test
    public void customAbbreviationListIsUsed() {
        var custom = new RegexTokenizer(Set.of("etc"));
        assertEquals(1, custom.tokenize("Apples etc. are fine.").size());
    }

    @Test
    public void customListReplacesDefaultsRatherThanAddingToThem() {
        var custom = new RegexTokenizer(Set.of("etc"));
        assertEquals(2, custom.tokenize("Mr. Darcy left.").size()); // "mr" is no longer an abbreviation, so abrupt end to sentence
    }

    @Test
    public void abbreviationAtVeryEndOfInputIsKept() {
        assertEquals(List.of(List.of("ask", "dr")), tokenizer.tokenize("Ask Dr."));
    }

    @Test
    public void dottedAbbreviationsAreSplitIntoFragments() {
        assertEquals(
                List.of(List.of("we", "like", "fruit", "e"), List.of("g"), List.of("apples")),
                tokenizer.tokenize("We like fruit, e.g. apples."));

        assertEquals(
                List.of(List.of("the", "u"), List.of("s"), List.of("is", "big")),
                tokenizer.tokenize("The U.S. is big."));
    }

    @Test
    public void abbreviationAloneIsKept() {
        assertEquals(List.of(List.of("dr")), tokenizer.tokenize("Dr."));
    }

    // edge cases for tokenizer

    @Test
    public void ellipsisAfterAbbreviationStillEndsSentence() {
        assertEquals(List.of(List.of("dr"), List.of("what")), tokenizer.tokenize("Dr... what"));
    }

    @Test
    public void abbreviationBeforeParagraphBreakStillEndsSentence() {
        assertEquals(2, tokenizer.tokenize("Ask Dr.\n\nHello there.").size());
    }

    // regex builder tests
    // ---- builder ----

    @Test
    public void builderWithDefaultsBehavesLikeDefaultConstructor() {
        var built = RegexTokenizer.builder().build();
        var text = "Prof. Cole meets us at 1:15 PM. Must be there.";
        assertEquals(tokenizer.tokenize(text), built.tokenize(text));
    }

    @Test
    public void appendPatternAddsNewTokenType() {
        var custom = RegexTokenizer.builder().appendPattern("#\\w+").build();
        assertEquals(List.of(List.of("love", "#java", "now")),
                custom.tokenize("Love #java now."));
    }

    @Test
    public void appendedPatternsHaveLowerPriorityThanDefaults() {
        // the default word pattern matches "me" first, so the email pattern never wins
        var custom = RegexTokenizer.builder().appendPattern("[a-z]+@[a-z]+").build();
        assertEquals(List.of(List.of("me", "site")), custom.tokenize("me@site"));
    }

    @Test
    public void customPatternsReplaceDefaults() {
        var custom = RegexTokenizer.builder()
                .customPatterns(List.of("[a-z]+", "[.]"))
                .build();
        // digits no longer match anything, so "123" is dropped
        assertEquals(List.of(List.of("abc"), List.of("def")), custom.tokenize("abc 123. def"));
    }

    @Test
    public void appendPatternExtendsCustomSet() {
        var custom = RegexTokenizer.builder()
                .customPatterns(List.of("[a-z]+"))
                .appendPattern("[.]")
                .build();
        assertEquals(List.of(List.of("hi"), List.of("there")), custom.tokenize("hi. there"));
    }

    @Test
    public void withoutAnEndPunctuationPatternEverythingIsOneSentence() {
        var custom = RegexTokenizer.builder().customPatterns(List.of("[a-z]+")).build();
        assertEquals(List.of(List.of("hello", "world")), custom.tokenize("hello. world"));
    }

    @Test
    public void builderAbbreviationsReplaceDefaults() {
        var custom = RegexTokenizer.builder().abbreviations(Set.of("etc")).build();
        assertEquals(1, custom.tokenize("Apples etc. are fine.").size());
        assertEquals(2, custom.tokenize("Mr. Darcy left.").size());
    }

    @Test
    public void abbreviationsAreCaseInsensitive() {
        var custom = RegexTokenizer.builder().abbreviations(Set.of("Etc")).build();
        assertEquals(1, custom.tokenize("Apples etc. are fine.").size());
    }

    @Test
    public void patternThatCanMatchEmptyStringDoesNotCrash() {
        var custom = RegexTokenizer.builder()
                .customPatterns(List.of("[a-z]+", "x*"))
                .build();
        assertEquals(List.of(List.of("hi", "there")), custom.tokenize("hi there"));
    }

    // invalid regex patterns
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ", // blank
        "(", // unclosed group
        "[a-z", // unclosed character class
        "*abc", // dangling metacharacter
        "a{2,1}", // illegal repetition range
        "\\" // trailing backslash
    })
    public void invalidPatternsAreRejected(String regex) {
        assertThrows(IllegalArgumentException.class,
                () -> RegexTokenizer.builder().appendPattern(regex).build());
    }

    @Test
    public void overlongPatternIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RegexTokenizer.builder().appendPattern("a".repeat(201)).build());
    }

    // regex validation
    @Test
    public void invalidRegexIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RegexTokenizer.builder().appendPattern("(").build());
    }

    @Test
    public void overlongOrBlankPatternIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RegexTokenizer.builder().appendPattern("a".repeat(201)).build());
        assertThrows(IllegalArgumentException.class,
                () -> RegexTokenizer.builder().appendPattern("   ").build());
    }

    @Test
    public void emptyPatternListIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RegexTokenizer.builder().customPatterns(List.of()).build());
    }

    @Test
    public void nullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> RegexTokenizer.builder().appendPattern(null));
        assertThrows(IllegalArgumentException.class, () -> RegexTokenizer.builder().customPatterns(null));
        assertThrows(IllegalArgumentException.class, () -> RegexTokenizer.builder().abbreviations(null));
        assertThrows(NullPointerException.class, () -> tokenizer.tokenize(null));
    }
}