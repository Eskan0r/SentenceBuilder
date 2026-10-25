package com.team23.sentencebuilder.io;

import java.time.Instant;

/**
 * What one imported text file contributed: its identity plus the counts that
 * were added to the session while reading it.
 *
 * @param fileName - name of the file as it sits on disk, without folders
 * @param location - absolute path of the imported file, for the user's reference
 * @param wordCount - how many words were taken from this file
 * @param sentenceCount - how many sentences were taken from this file
 * @param newWords - words that appeared in no earlier import of this session
 * @param importedAt - when the import finished
 *
 * @author Ronak Chavva
 */
public record ImportResult(String fileName,
                           String location,
                           int wordCount,
                           int sentenceCount,
                           int newWords,
                           Instant importedAt) {
}
