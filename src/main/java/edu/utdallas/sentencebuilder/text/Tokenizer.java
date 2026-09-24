package edu.utdallas.sentencebuilder.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns raw text into sentences made of words.
 * <p>
 * <b>What is a word?</b>  For this project a word is a maximal run of letters
 * or digits, which may contain a single apostrophe or hyphen <i>between</i>
 * two such characters ({@code don't}, {@code mother-in-law}, {@code 1815}).
 * Everything is lower-cased, so {@code The} and {@code the} are the same word.
 * Leading/trailing punctuation and quotes are dropped ({@code "Hello,"} is the
 * word {@code hello}); Gutenberg's {@code _italics_} underscores are dropped
 * too.  Curly apostrophes are normalised to {@code '}.
 * <p>
 * <b>What is a sentence?</b>  A run of words ended by {@code .}, {@code !} or
 * {@code ?}.  A blank line (paragraph break) also ends a sentence, which
 * keeps chapter headings such as {@code CHAPTER I} from being glued to the
 * first sentence of the chapter.  Abbreviations ({@code Mr.}) are treated as
 * sentence ends, which is a known and accepted simplification.
 */
public final class Tokenizer {

    /** Longest word we will store; the {@code word.text} column is VARCHAR(64). */
    public static final int MAX_WORD_LENGTH = 64;

    private Tokenizer() {
    }

    /** Convenience for small inputs: parses the whole string in memory. */
    public static List<List<String>> sentences(String text) {
        List<List<String>> out = new ArrayList<>();
        SentenceBuilder sb = new SentenceBuilder(out::add);
        for (String line : text.split("\r?\n", -1)) {
            sb.feedLine(line);
        }
        sb.finish();
        return out;
    }

    /** Splits one line into words, ignoring sentence boundaries. */
    public static List<String> words(String line) {
        List<String> out = new ArrayList<>();
        SentenceBuilder sb = new SentenceBuilder(out::addAll);
        sb.feedLine(line);
        sb.finish();
        return out;
    }

    static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }

    static boolean isJoiner(char c) {
        return c == '\'' || c == '-';
    }

    static boolean isSentenceEnd(char c) {
        return c == '.' || c == '!' || c == '?';
    }

    static char normalise(char c) {
        return switch (c) {
            case '’', '‘', 'ʼ' -> '\'';   // curly / modifier apostrophes
            case '‐', '‑', '‒' -> '-';    // unicode hyphens
            default -> c;
        };
    }

    /**
     * Incremental sentence assembler.  Feed it lines; it calls the sink with
     * each complete sentence.  Used by {@link Tokenizer#sentences(String)} and
     * by the streaming file importer so that a 5 MB novel is never held in
     * memory as a single string of tokens.
     */
    public static final class SentenceBuilder {

        private final java.util.function.Consumer<List<String>> sink;
        private List<String> current = new ArrayList<>();
        private final StringBuilder token = new StringBuilder();

        public SentenceBuilder(java.util.function.Consumer<List<String>> sink) {
            this.sink = sink;
        }

        public void feedLine(String line) {
            if (line.isBlank()) {
                flushToken();
                endSentence();
                return;
            }
            int n = line.length();
            for (int i = 0; i < n; i++) {
                char c = normalise(line.charAt(i));
                if (isWordChar(c)) {
                    token.append(Character.toLowerCase(c));
                } else if (isJoiner(c) && token.length() > 0
                        && i + 1 < n && isWordChar(normalise(line.charAt(i + 1)))) {
                    token.append(c);          // internal apostrophe / hyphen
                } else {
                    flushToken();
                    if (isSentenceEnd(c)) {
                        endSentence();
                    }
                }
            }
            flushToken();                     // end of line ends any token
        }

        /** Call once after the last line so a trailing sentence without a period is not lost. */
        public void finish() {
            flushToken();
            endSentence();
        }

        private void flushToken() {
            if (token.length() > 0) {
                String w = token.toString();
                if (w.length() <= MAX_WORD_LENGTH) {
                    current.add(w);
                }
                token.setLength(0);
            }
        }

        private void endSentence() {
            if (!current.isEmpty()) {
                sink.accept(current);
                current = new ArrayList<>();
            }
        }
    }

    /** Formats a word list back into a readable sentence: capital first letter, period at the end. */
    public static String format(List<String> words) {
        if (words.isEmpty()) {
            return "";
        }
        String joined = String.join(" ", words);
        return joined.substring(0, 1).toUpperCase(Locale.ROOT) + joined.substring(1) + ".";
    }
}
