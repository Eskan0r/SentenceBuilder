package com.team23.sentencebuilder.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extension to Tokenizer that's based off Regex patterns.
 * <p>
 * A word is a maximal run of letters and/or digits that join with separators such
 * as apostrophes or hyphens (like "don't" or "well-known") and are typically lowercased.
 * <p>
 * Scenarios that end a sentence:
 * - A run of '.', '!', or '?' ends a sentence unless the period follows some known
 * abbreviation (like with titles).
 * - A blank line (or paragraph break)
 * <p>
 * When we end a sentence, here we discard punctuation for word generation to work.
 *
 * @author Alen Jo
 */
public class RegexTokenizer implements Tokenizer {
    public static final Set<String> DEFAULT_ABBREVIATIONS = Set.of(
            "mr", "mrs", "ms", "dr", "st", "jr", "sr", "prof", "vs", "mt");

    private static final Pattern TOKEN = Pattern.compile(
            "[\\p{L}\\p{N}]+(?:['\u2019\\-][\\p{L}\\p{N}]+)*|[.!?]+");

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\R\\s*\\R");

    private final Set<String> abbreviations;

    public RegexTokenizer()
    {
        this(DEFAULT_ABBREVIATIONS);
    }

    public RegexTokenizer(Set<String> abbreviations)
    {
        this.abbreviations = abbreviations;
    }

    /**
     * Creates sentences from a paragraph of text
     *
     * @param text - paragraph of text
     * @return - sentences from a paragraph of text
     */
    @Override
    public List<List<String>> tokenize(String text) {
        List<List<String>> sentences = new ArrayList<>();
        for (String paragraph : PARAGRAPH_BREAK.split(text)) {
            tokenizeParagraph(paragraph, sentences);
        }
        return sentences;
    }

    /**
     * Normalizes a word via removing curly apostrophes and defaulting to system locale
     *
     * @param text - raw word or text
     * @return - normalized word stripped of curly apostrophes and converted to the system locale
     */
    @Override
    public String normalize(String text) {
        return text.replace('\u2019', '\'').toLowerCase(Locale.ROOT);
    }

    /**
     * Splits a paragraph into list of list representations of tokens
     *
     * @param paragraph - raw paragraph text
     * @param out - 2D representation of paragraph into tokens that are organized into sentences
     */
    private void tokenizeParagraph(String paragraph, List<List<String>> out)
    {
        List<String> current = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(paragraph);

        while (matcher.find()) {
            String token = matcher.group();

            if (isEndPunctuation(token)) {
                if (!isAbbreviationPeriod(token, current) && !current.isEmpty()) {
                    out.add(current);
                    current = new ArrayList<>();
                }
            } else {
                current.add(token);
            }
        }

        if (!current.isEmpty()) {
            out.add(current);
        }
    }

    /**
     * Whether a token is an end punctuation that signals an end to a sentence
     * @param token - word or text
     * @return - whether a token is a punctuation that ends a sentence or false
     */
    private boolean isEndPunctuation(String token)
    {
        char tokenCharacter = token.charAt(0);
        return tokenCharacter == '.' || tokenCharacter == '!' || tokenCharacter == '?';
    }

    /**
     * Whether a punctuation qualifies as an abbreviation since that doesn't signal
     * an end of a sentence but truncates the fully-qualified name of a word
     *
     * @param token - word or token
     * @param current - all tokens collected earlier
     * @return - whether a token is an abbreviation or not
     */
    private boolean isAbbreviationPeriod(String token, List<String> current)
    {
        return token.equals(".")
            && !current.isEmpty()
            && abbreviations.contains(current.getLast());
    }
}
