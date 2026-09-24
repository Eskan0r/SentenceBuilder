-- Sentence Builder schema (MySQL 8 / MariaDB 10.5+)
-- The application runs this file automatically at startup (every statement is
-- idempotent), so you can also paste it into MySQL Workbench if you prefer to
-- create the tables by hand.

CREATE DATABASE IF NOT EXISTS sentence_builder
    CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE sentence_builder;

-- One row per distinct word.  "text" is stored lower-cased.  Its collation is
-- pinned to utf8mb4_bin (regardless of the database default) so that words
-- differing only in accents, e.g. "fiance" and "fiancé", stay distinct rows;
-- with MySQL's default accent-insensitive collation they would collide on the
-- unique key.
CREATE TABLE IF NOT EXISTS word (
    word_id     INT UNSIGNED NOT NULL AUTO_INCREMENT,
    text        VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    total_count INT UNSIGNED NOT NULL DEFAULT 0,   -- occurrences anywhere
    start_count INT UNSIGNED NOT NULL DEFAULT 0,   -- occurrences as first word of a sentence
    end_count   INT UNSIGNED NOT NULL DEFAULT 0,   -- occurrences as last word of a sentence
    first_seen  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (word_id),
    UNIQUE KEY uk_word_text (text),
    KEY ix_word_total (total_count),
    KEY ix_word_start (start_count),
    KEY ix_word_end   (end_count)
) ENGINE = InnoDB;

-- Bigram table: how many times next_word immediately followed word.
-- chosen_count is bumped every time a user picks/types next_word after word in
-- the auto-complete screen, so the UI can learn from the user separately from
-- the imported corpus.
CREATE TABLE IF NOT EXISTS word_follow (
    word_id      INT UNSIGNED NOT NULL,
    next_word_id INT UNSIGNED NOT NULL,
    count        INT UNSIGNED NOT NULL DEFAULT 0,
    chosen_count INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (word_id, next_word_id),
    KEY ix_follow_next (next_word_id),
    KEY ix_follow_count (word_id, count),
    CONSTRAINT fk_follow_word      FOREIGN KEY (word_id)      REFERENCES word (word_id),
    CONSTRAINT fk_follow_next_word FOREIGN KEY (next_word_id) REFERENCES word (word_id)
) ENGINE = InnoDB;

-- Every import, whether it came from a text file on disk, from sentences the
-- program generated and fed back into itself, or from the auto-complete screen.
CREATE TABLE IF NOT EXISTS imported_file (
    file_id        INT UNSIGNED NOT NULL AUTO_INCREMENT,
    file_name      VARCHAR(255) NOT NULL,
    file_path      VARCHAR(1024) NULL,
    source_type    ENUM('TEXT_FILE', 'GENERATED', 'AUTOCOMPLETE') NOT NULL DEFAULT 'TEXT_FILE',
    byte_size      BIGINT UNSIGNED NULL,
    word_count     INT UNSIGNED NOT NULL DEFAULT 0,   -- total tokens in the file
    sentence_count INT UNSIGNED NOT NULL DEFAULT 0,
    distinct_words INT UNSIGNED NOT NULL DEFAULT 0,   -- distinct tokens in this file
    new_words      INT UNSIGNED NOT NULL DEFAULT 0,   -- tokens that were not in the database before
    import_millis  INT UNSIGNED NULL,                 -- how long the import took
    imported_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (file_id),
    KEY ix_file_imported_at (imported_at)
) ENGINE = InnoDB;

-- History of every sentence the program generated.  sentence_hash lets the
-- reports screen find duplicates cheaply.  fed_back_file_id is set once the
-- sentence has been imported back into the corpus.
CREATE TABLE IF NOT EXISTS generated_sentence (
    sentence_id      INT UNSIGNED NOT NULL AUTO_INCREMENT,
    sentence_text    TEXT         NOT NULL,
    sentence_hash    CHAR(64)     NOT NULL,   -- SHA-256 of sentence_text
    generator_name   VARCHAR(64)  NOT NULL,
    start_word       VARCHAR(64)  NOT NULL,
    random_start     TINYINT(1)   NOT NULL DEFAULT 0,
    word_count       INT UNSIGNED NOT NULL DEFAULT 0,
    generated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fed_back_file_id INT UNSIGNED NULL,
    PRIMARY KEY (sentence_id),
    KEY ix_sentence_hash (sentence_hash),
    KEY ix_sentence_generated_at (generated_at),
    CONSTRAINT fk_sentence_file FOREIGN KEY (fed_back_file_id) REFERENCES imported_file (file_id)
) ENGINE = InnoDB;
