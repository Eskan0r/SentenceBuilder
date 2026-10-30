package com.team23.sentencebuilder.io;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Specialized stripper that helps remove Gutenberg markers
 * from text to be used for downstream services such as TextImporter
 * or database upsert or etc
 *
 * @author Alen Jo
 */
public class GutenbergStripper implements BoilerplateStripper {
    private static final List<String> START_MARKERS = List.of(
        "*** START OF THE PROJECT GUTENBERG EBOOK",
        "*** START OF THIS PROJECT GUTENBERG EBOOK",
        "***START OF THE PROJECT GUTENBERG EBOOK"
    );

    private static final List<String> END_MARKERS = List.of(
        "*** END OF THE PROJECT GUTENBERG EBOOK",
        "*** END OF THIS PROJECT GUTENBERG EBOOK",
        "***END OF THE PROJECT GUTENBERG EBOOK"
    );

    /**
     * Strips out start and end markers of a provided text to which here it is our
     * Gutenberg markers
     *
     * @param rawText - input text from I/O
     *
     * @return - text without start and end markers if markers were present respectively
     */
    @Override
    public String strip(String rawText) {
        Objects.requireNonNull(rawText, "required raw text for stripping to work!");

        List<String> lines = rawText.lines().toList();

        int start = indexOfMarker(lines, START_MARKERS, 0);

        if (start < 0) return rawText; // no start

        int end = indexOfMarker(lines, END_MARKERS, start + 1);
        int stop = (end < 0) ? lines.size() : end; // if no end, take everything

        return String.join("\n", lines.subList(start + 1, stop));
    }

    /**
     * Returns the marker index from all lines within a text
     *
     * @param lines - text lines from input
     * @param markers - defined markers to detect for a text
     * @param from - where to start the search
     *
     * @return - marker index or -1 if not found
     */
    private static int indexOfMarker(List<String> lines, List<String> markers, int from) {
        for (int currentStringIdx = from; currentStringIdx < lines.size(); currentStringIdx++) {
            String normalized = lines.get(currentStringIdx).strip().toUpperCase(Locale.ROOT);

            for (String marker : markers) {
                if (normalized.startsWith(marker)) {
                    return currentStringIdx;
                }
            }
        }

        return -1;
    }
}
