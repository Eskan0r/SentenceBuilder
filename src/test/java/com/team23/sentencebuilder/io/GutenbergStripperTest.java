package com.team23.sentencebuilder.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class GutenbergStripperTest {
    private static final String START = "*** START OF THE PROJECT GUTENBERG EBOOK MOBY DICK ***";
    private static final String END = "*** END OF THE PROJECT GUTENBERG EBOOK MOBY DICK ***";

    private BoilerplateStripper stripper;

    @BeforeEach
    void setUp() {
        stripper = new GutenbergStripper();
    }

    private static String text(String... lines) {
        return String.join("\n", lines);
    }

    /*
        Positive cases to check
    */

    @Test
    void stripsHeaderAndFooter() {
        String raw = text("Title: Moby Dick", "License blurb", START,
                "Call me Ishmael.", "Some years ago...", END, "Legal footer");

        assertEquals(text("Call me Ishmael.", "Some years ago..."),
                stripper.strip(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "*** START OF THE PROJECT GUTENBERG EBOOK X ***",
            "*** START OF THIS PROJECT GUTENBERG EBOOK X ***",
            "***START OF THE PROJECT GUTENBERG EBOOK X ***"
    })
    void recognizesAllStartVariants(String startLine) {
        String raw = text("header", startLine, "body", END);

        assertEquals("body", stripper.strip(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "*** END OF THE PROJECT GUTENBERG EBOOK X ***",
            "*** END OF THIS PROJECT GUTENBERG EBOOK X ***",
            "***END OF THE PROJECT GUTENBERG EBOOK X ***"
    })
    void recognizesAllEndVariants(String endLine) {
        String raw = text(START, "body", endLine, "footer");

        assertEquals("body", stripper.strip(raw));
    }

    /*
    Rule matching
    */

    @Test
    void matchingIsCaseInsensitive() {
        String raw = text("header",
                "*** start of the project gutenberg ebook x ***",
                "body",
                "*** End Of The Project Gutenberg eBook x ***",
                "footer");

        assertEquals("body", stripper.strip(raw));
    }

    @Test
    void ignoresLeadingAndTrailingWhitespaceOnMarkerLines() {
        String raw = text("header", "   " + START + "   ", "body",
                "\t" + END, "footer");

        assertEquals("body", stripper.strip(raw));
    }

    @Test
    void markerQuotedMidLineIsNotAMatch() {
        String raw = text("The intro says: " + START, "body text");


        assertEquals(raw, stripper.strip(raw)); // No line *starts* with a marker, therefore nothing stripped.
    }

    @Test
    void endMarkerQuotedMidLineInBodyDoesNotCutEarly() {
        String quoted = "Editors often write " + END + " to close a file.";
        String raw = text(START, "line one", quoted, "line three", END, "footer");

        assertEquals(text("line one", quoted, "line three"), stripper.strip(raw));
    }

    /*
    Missing markers
    */

    @Test
    void noStartMarkerReturnsTextUnchanged() {
        String raw = text("Just some text", "with no markers");

        assertEquals(raw, stripper.strip(raw));
    }

    @Test
    void noStartButEndPresentReturnsTextUnchanged() {
        String raw = text("some text", "more text", END, "footer");

        assertEquals(raw, stripper.strip(raw));
    }

    @Test
    void startWithoutEndReturnsEverythingAfterStart() {
        String raw = text("header", START, "body one", "body two");

        assertEquals(text("body one", "body two"), stripper.strip(raw));
    }

    @Test
    void endMarkerBeforeStartIsIgnored() {
        String raw = text(END, "stray header", START, "body", END, "footer");

        assertEquals("body", stripper.strip(raw));
    }

    @Test
    void firstEndMarkerAfterStartWins() {
        String raw = text(START, "body", END, "middle", END, "tail");

        assertEquals("body", stripper.strip(raw));
    }

    /*
    Edge/boundary cases
    */
    @Test
    void emptyBodyBetweenMarkersReturnsEmptyString() {
        String raw = text("header", START, END, "footer");

        assertEquals("", stripper.strip(raw));
    }

    @Test
    void emptyInputReturnsEmptyString() {
        assertEquals("", stripper.strip(""));
    }

    @Test
    void crlfInputIsHandledAndNormalizedToLf() {
        String raw = "header\r\n" + START + "\r\nline one\r\nline two\r\n" + END + "\r\nfooter";

        assertEquals("line one\nline two", stripper.strip(raw));
    }

    @Test
    void preservesBlankLinesInsideBody() {
        String raw = text(START, "para one", "", "para two", END);

        assertEquals(text("para one", "", "para two"), stripper.strip(raw));
    }

    @Test
    void nullInputThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> stripper.strip(null));
    }
}
