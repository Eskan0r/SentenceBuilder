package com.team23.sentencebuilder.database;

public class SQLTables {

    public static final String DDLScript = """
        CREATE TABLE IF NOT EXISTS words (
            word                        VARCHAR(100) NOT NULL,
            count                       INT NOT NULL DEFAULT 0,
            occurence_start_of_sentence INT NOT NULL DEFAULT 0,
            occurence_end_of_sentence   INT NOT NULL DEFAULT 0,
            PRIMARY KEY (word)
        );

        CREATE TABLE IF NOT EXISTS follow (
            word                        VARCHAR(100) NOT NULL,
            following_word              VARCHAR(100) NOT NULL,
            occurence_of_following_word INT NOT NULL DEFAULT 0,
            PRIMARY KEY (word, following_word)
        );

        CREATE TABLE IF NOT EXISTS file (
            file_name        VARCHAR(255) NOT NULL,
            total_word_count INT NOT NULL DEFAULT 0,
            time_stamp       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (file_name)
        );
        """;
}
