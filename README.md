# Sentence Builder

CS4485 Senior Design, Fall 2026 (Prof. John Cole).

A small "auto-complete on steroids": the program reads text files, records every
word together with how often it occurs, starts a sentence, ends a sentence and
which words follow it, and then uses those counts to generate sentences, to feed
its own output back in as new text, and to offer next-word suggestions while you
type. Java 21, JavaFX 21, MySQL 8 (MariaDB works too).

## Running it

Prerequisites: JDK 21 and Maven. A MySQL server is optional (see below).

```bash
mvn javafx:run
```

### Choosing a database

The app can store its data in either of two places. Both use the same SQL,
schema and code; only the JDBC URL differs.

- **Built-in database (no setup).** An H2 database file in
  `~/.sentencebuilder/`, run in MySQL-compatibility mode. On the first start,
  if no MySQL server answers, the app offers this with one click. Good for
  development and demos.
- **MySQL / MariaDB server** (what the assignment requires for the final
  product). Pick *Database > Settings…* in the menu, choose the server option
  and enter the URL, user and password. The database and all tables are
  created automatically from `src/main/resources/db/schema.sql` (you can also
  paste that file into MySQL Workbench).

The choice is saved to `~/.sentencebuilder/db.properties`; `db.url=embedded`
means the built-in database. `mvn test` runs the full database pipeline on an
in-memory H2 instance, so no server is needed for the tests either.

Other ways to run:

- **IntelliJ IDEA**: open the folder (it is a Maven project), then run the
  Maven goal `javafx:run`, or run `App` directly after adding the JavaFX VM
  options IntelliJ suggests.
- **Fat jar**: `mvn package` produces `target/sentence-builder.jar`;
  run it with `java -jar target/sentence-builder.jar`.
- Override the connection without the dialog:
  `mvn javafx:run -Dsb.db.url=... -Dsb.db.user=... -Dsb.db.password=...`

A cleaned copy of *Pride and Prejudice* is in `samples/` to get started
(Gutenberg header and footer already removed). More books: https://gutenberg.org,
"Plain Text UTF-8" format.

## What the screens do

| Tab | Requirement it covers |
| --- | --- |
| **Import** | Queue text files, import them with a progress bar, see the history of everything imported (files, generated batches, auto-complete sessions) with word counts and timestamps. |
| **Generate** | Build N sentences from a start word you type, or from random start words. Four algorithms (see below). Every sentence is saved to the history. The *Add these sentences to the corpus* button feeds the batch back in as input text. |
| **Auto-complete** | Type freely. When a word is completed (space, comma, period) the list shows what most often comes next; insert with double-click or Enter. Every typed word is added or counted, and each (previous, next) pair you use gets its `count` and `chosen_count` incremented. |
| **Words** | Browse every word with filter (`*` wildcard), minimum count, seven sort orders and a row limit. Counts are editable in place. Selecting a word shows its followers (count editable) and predecessors. |
| **Reports** | Corpus summary numbers; the generated-sentence history with a *Duplicates only* switch and text filter; most common sentence starters and enders; frequent words that never start a sentence. |

## What is a word?

A word is a maximal run of letters or digits, which may contain a single
apostrophe or hyphen *between* two such characters (`don't`, `mother-in-law`,
`1815`). Everything is lower-cased, so `The` and `the` are one word. Quotes,
brackets, Gutenberg `_italics_` underscores and other punctuation are dropped.
A sentence ends at `.`, `!`, `?` or a blank line. Abbreviations such as `Mr.`
therefore end a sentence; this is a known simplification. See `Tokenizer`.

## Generators

All extend the abstract `SentenceGenerator`, which owns the loop (start word,
ask the subclass for the next word until it says "stop", enforce min/max
length). Ending the sentence is offered as one more weighted candidate whose
weight is the current word's end-of-sentence count.

| Name | Rule |
| --- | --- |
| Weighted random *(required probabilistic one)* | Each follower is chosen in proportion to how often it followed the current word, so rare continuations still appear occasionally. |
| Top-5 sampling | Same, but only among the five most frequent followers (how LLMs keep output coherent). |
| Uniform random | Every follower equally likely, ignoring counts. |
| Most frequent | Always the most common follower; refuses to reuse a word pair so it cannot loop. Deterministic. |

## Database

```
word               word_id, text (unique), total_count, start_count, end_count, first_seen, last_seen
word_follow        word_id, next_word_id, count, chosen_count      (bigram table)
imported_file      file_id, file_name, file_path, source_type, byte_size, word_count,
                   sentence_count, distinct_words, new_words, import_millis, imported_at
generated_sentence sentence_id, sentence_text, sentence_hash, generator_name, start_word,
                   random_start, word_count, generated_at, fed_back_file_id
```

Imports are batched: the file is streamed through the tokenizer into an
in-memory `CorpusCounts` tally, then merged with `INSERT ... ON DUPLICATE KEY
UPDATE` in batches of 2 000 inside one transaction (`CorpusWriter`). A 700 KB
novel imports in a few seconds.

## Code map

```
edu.utdallas.sentencebuilder
├── App, Launcher, AppContext        JavaFX entry point and service wiring
├── config.DbSettings                where the DB is; ~/.sentencebuilder/db.properties
├── db.Database, SchemaInstaller     connection pool, runs schema.sql on start-up
├── text.Tokenizer                   what a word / sentence is
├── corpus.CorpusCounts              in-memory tally of a text
├── corpus.CorpusWriter              batched merge of a tally into MySQL
├── corpus.ImportService             import a file, or feed generated sentences back
├── lm.LanguageModel                 read-only bigram view (Jdbc… and InMemory… impls)
├── generate.SentenceGenerator       abstract algorithm + 4 subclasses + GeneratorFactory
├── autocomplete.AutoCompleteService suggestions and learning from the user
├── repo.*Repository                 browsing/editing words, files, sentence history
├── model.*                          records passed between layers
└── ui.*Controller + fxml/*.fxml     one controller per tab
```

## Tests

```bash
mvn test
```

Unit tests cover the tokenizer, the counting and all four generators (using
the in-memory model). `DatabaseIntegrationTest` runs the whole pipeline
(import, generate, feed back, auto-complete, browsing, editing, reports)
against an in-memory H2 database by default. To run it against a real MySQL
server instead, point it at a scratch database (it drops the tables there):

```bash
mvn test -Dsb.it.url="jdbc:mysql://localhost:3306/sentence_builder_test?createDatabaseIfNotExist=true&rewriteBatchedStatements=true" -Dsb.it.user=root -Dsb.it.password=secret
```
