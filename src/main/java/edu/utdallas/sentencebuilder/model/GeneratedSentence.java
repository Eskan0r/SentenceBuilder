package edu.utdallas.sentencebuilder.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * A sentence produced by one of the generators.  {@code id} is 0 until the
 * sentence has been saved; {@code duplicates} is filled in by the reports
 * query and says how many times this exact text has been generated in total.
 */
public record GeneratedSentence(
        int id,
        String text,
        List<String> words,
        String generatorName,
        String startWord,
        boolean randomStart,
        LocalDateTime generatedAt,
        Integer fedBackFileId,
        int duplicates) {

    public static String hashOf(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String hash() {
        return hashOf(text);
    }

    public boolean fedBack() {
        return fedBackFileId != null;
    }

    public GeneratedSentence withId(int newId) {
        return new GeneratedSentence(newId, text, words, generatorName, startWord, randomStart,
                generatedAt, fedBackFileId, duplicates);
    }
}
