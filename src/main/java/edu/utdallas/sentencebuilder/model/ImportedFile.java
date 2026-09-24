package edu.utdallas.sentencebuilder.model;

import java.time.LocalDateTime;

/** One row of {@code imported_file}. */
public record ImportedFile(
        int id,
        String fileName,
        String filePath,
        SourceType sourceType,
        long byteSize,
        int wordCount,
        int sentenceCount,
        int distinctWords,
        int newWords,
        int importMillis,
        LocalDateTime importedAt) {

    public enum SourceType { TEXT_FILE, GENERATED, AUTOCOMPLETE }
}
