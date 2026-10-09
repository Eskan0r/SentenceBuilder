package com.team23.sentencebuilder.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Tokenizer implementation based on regex patterns.
 * <p>
 * A word is a maximal run of letters and/or digits, optionally joined by
 * separators such as apostrophes or hyphens (like "don't" or "well-known").
 * Words are lowercased during normalization. Numbers may contain ':', '.' or ','
 * between digits (1:15, 3.5, 1,000), and dotted abbreviations such as "U.S." or
 * "p.m." are kept whole, including their periods.
 * <p>
 * Scenarios that end a sentence:
 * <ul>
 *   <li>A run of '.', '!', or '?' ends a sentence, unless the period follows a
 *       known abbreviation (such as a title like "Dr").</li>
 *   <li>A blank line (paragraph break).</li>
 * </ul>
 * Sentence-ending punctuation is consumed and is not included in the output tokens.
 * A dotted abbreviation at the end of a real sentence does not end it; this is a
 * known simplification.
 * <p>
 * Patterns are combined into a single alternation and tried in order at each
 * position, so earlier patterns take priority over later ones. Patterns added
 * via {@link Builder#appendPattern(String)} are tried after the defaults, so in
 * practice they only see text the defaults do not match (emoticons, symbols).
 *
 * @author Alen Jo
 */
public class RegexTokenizer implements Tokenizer {
    public static final Set<String> DEFAULT_ABBREVIATIONS = Set.of(
            "mr", "mrs", "ms", "dr", "st", "jr", "sr", "prof", "vs", "mt");

    private static final char CURLY_APOSTROPHE = '\u2019'; // Typographic apostrophe found in Gutenberg mateerial

    private static final List<String> DEFAULT_PATTERNS = List.of(
            "\\p{N}+(?:[:.,]\\p{N}+)+", // numbers: 1:15, 3.5, 1,000
            "(?<![\\p{L}\\p{N}])(?:\\p{L}\\.){2,}", // dotted abbreviations: e.g., U.S., a.m.
            "[\\p{L}\\p{N}]+(?:['" + CURLY_APOSTROPHE + "\\-][\\p{L}\\p{N}]+)*", // words
            "[.!?]+" // sentence-ending punctuation
    );

    private static final int MAX_PATTERN_LENGTH = 200;

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\R\\s*\\R");

    private final Pattern tokenPattern;
    private final Set<String> abbreviations;

    /**
     * Creates a tokenizer with the default patterns and abbreviation list.
     */
    public RegexTokenizer() {
        this(DEFAULT_PATTERNS, DEFAULT_ABBREVIATIONS);
    }

    /**
     * Creates a tokenizer with the default patterns and a custom abbreviation list.
     *
     * @param abbreviations - words whose trailing period does not end a sentence; case-insensitive
     */
    public RegexTokenizer(Set<String> abbreviations) {
        this(DEFAULT_PATTERNS, abbreviations);
    }

    /**
     * Creates a tokenizer from explicit patterns and abbreviations. Private so that
     * outside code goes through the public constructors or the {@link Builder}.
     *
     * @param patterns - regexes combined into one alternation, in priority order
     * @param abbreviations - words whose trailing period does not end a sentence
     */
    private RegexTokenizer(List<String> patterns, Set<String> abbreviations) {
        Objects.requireNonNull(abbreviations, "Abbreviations cannot be null");
        this.tokenPattern = compilePatterns(patterns);

        // Tokens are lowercased before the abbreviation check, so we store them lowercased.
        this.abbreviations = abbreviations.stream()
                .map(abbreviation -> abbreviation.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Starts a builder for a tokenizer with custom patterns or abbreviations.
     *
     * @return a new builder preloaded with the default patterns and abbreviations
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates sentences from a block of text.
     *
     * @param text - text that may span multiple paragraphs
     * @return - sentences, each represented as a list of normalized tokens
     */
    @Override
    public List<List<String>> tokenize(String text) {
        Objects.requireNonNull(text, "Text cannot be null");

        List<List<String>> sentences = new ArrayList<>();

        // Paragraphs are tokenized separately so a sentence can never run across
        // a blank line (otherwise a heading would glue onto the next sentence).
        for (String paragraph : PARAGRAPH_BREAK.split(text)) {
            sentences.addAll(tokenizeParagraph(paragraph));
        }
        return sentences;
    }

    /**
     * Normalizes a word by replacing curly apostrophes with straight ones
     * and lowercasing (locale-independent).
     *
     * @param text - raw word or text
     * @return - normalized word
     */
    @Override
    public String normalize(String text) {
        return text.replace(CURLY_APOSTROPHE, '\'').toLowerCase(Locale.ROOT);
    }

    /**
     * Splits a paragraph into sentences of normalized tokens.
     *
     * @param paragraph - raw paragraph text containing no blank lines
     * @return - the sentences found in the paragraph, in order
     */
    private List<List<String>> tokenizeParagraph(String paragraph) {
        List<List<String>> sentences = new ArrayList<>();
        List<String> current = new ArrayList<>();
        Matcher matcher = tokenPattern.matcher(paragraph);

        while (matcher.find()) {
            String token = matcher.group();

            // Custom patterns could match the empty string; those matches are ignored.
            if (!token.isEmpty()) {
                if (isEndPunctuation(token)) {
                    // An abbreviation period, or a terminator with no words before it
                    if (!isAbbreviationPeriod(token, current) && !current.isEmpty()) {
                        sentences.add(current);
                        current = new ArrayList<>();
                    }
                } else {
                    current.add(normalize(token));
                }
            }
        }

        // Text after the last terminator (or with no terminator) is still a sentence.
        if (!current.isEmpty()) {
            sentences.add(current);
        }
        return sentences;
    }

    /**
     * Whether a token is end punctuation that signals the end of a sentence.
     *
     * @param token - non-empty token
     * @return - true if the token starts with '.', '!', or '?'
     */
    private boolean isEndPunctuation(String token) {
        char first = token.charAt(0);
        return first == '.' || first == '!' || first == '?';
    }

    /**
     * Whether a period belongs to an abbreviation (like "Dr.") and therefore
     * does not end the sentence.
     *
     * @param token - the punctuation token
     * @param current - tokens collected so far in the current sentence
     * @return - true if the token is a lone period following a known abbreviation
     */
    private boolean isAbbreviationPeriod(String token, List<String> current) {
        return token.equals(".")
                && !current.isEmpty()
                && abbreviations.contains(current.get(current.size() - 1));
    }

    /**
     * Validates each pattern individually (for clearer error messages), then
     * combines them into a single alternation.
     *
     * @param patterns - regexes in priority order; none may be null, blank, or too long
     * @return - one compiled pattern that tries each regex in order
     * @throws IllegalArgumentException if the list is empty or any regex is invalid
     */
    private static Pattern compilePatterns(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            throw new IllegalArgumentException("At least one token pattern is required");
        }

        List<String> grouped = new ArrayList<>();

        for (String regex : patterns) {
            if (regex == null || regex.isBlank() || regex.length() > MAX_PATTERN_LENGTH) {
                throw new IllegalArgumentException("Invalid token pattern length or value");
            }

            try {
                Pattern.compile(regex);
            } catch (PatternSyntaxException syntaxError) {
                throw new IllegalArgumentException("Invalid token pattern: " + regex, syntaxError);
            }

            grouped.add("(?:" + regex + ")");
        }

        return Pattern.compile(String.join("|", grouped));
    }

    public static final class Builder {
        private List<String> patterns = new ArrayList<>(DEFAULT_PATTERNS);
        private Set<String> abbreviations = DEFAULT_ABBREVIATIONS;

        private Builder() {
        }

        /**
         * Adds a pattern after the existing ones (the defaults, or the custom
         * set if {@link #customPatterns(List)} was called). Patterns are tried
         * in order, so appended patterns have the lowest priority.
         */
        public Builder appendPattern(String regex) {
            if (regex == null) {
                throw new IllegalArgumentException("Pattern cannot be null");
            }

            patterns.add(regex);
            return this;
        }

        /**
         * Replaces all current patterns with the supplied patterns.
         * Subsequent {@link #appendPattern(String)} calls extend this custom set.
         */
        public Builder customPatterns(List<String> regexes) {
            if (regexes == null) {
                throw new IllegalArgumentException("Patterns cannot be null");
            }

            patterns = new ArrayList<>(regexes);
            return this;
        }

        /**
         * Replaces the set of abbreviations whose trailing period does not end
         * a sentence. Case-insensitive.
         */
        public Builder abbreviations(Set<String> abbreviations) {
            if (abbreviations == null) {
                throw new IllegalArgumentException("Abbreviations cannot be null");
            }

            this.abbreviations = abbreviations;
            return this;
        }

        /**
         * Builds a tokenizer from the patterns and abbreviations set so far.
         * Invalid patterns are reported here, not when they were added.
         *
         * @return a new tokenizer
         * @throws IllegalArgumentException if any pattern is invalid
         */
        public RegexTokenizer build() {
            return new RegexTokenizer(patterns, abbreviations);
        }
    }
}