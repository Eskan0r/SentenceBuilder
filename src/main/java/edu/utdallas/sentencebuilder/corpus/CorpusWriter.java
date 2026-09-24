package edu.utdallas.sentencebuilder.corpus;

import edu.utdallas.sentencebuilder.db.Database;
import edu.utdallas.sentencebuilder.model.ImportedFile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges a {@link CorpusCounts} tally into the database inside one
 * transaction.  Everything is done with batched
 * {@code INSERT ... ON DUPLICATE KEY UPDATE} statements, so importing a whole
 * novel costs a few hundred round-trips instead of a few hundred thousand.
 */
public final class CorpusWriter {

    private static final int BATCH = 2_000;

    private final Database db;

    public CorpusWriter(Database db) {
        this.db = db;
    }

    /**
     * Writes the counts and records the import in {@code imported_file}.
     *
     * @return the saved {@link ImportedFile} row, including its new id
     */
    public ImportedFile merge(CorpusCounts counts, String fileName, String filePath,
                              ImportedFile.SourceType sourceType, long byteSize, long startedAtMillis,
                              ProgressListener progress) throws SQLException {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                List<String> texts = new ArrayList<>(counts.words().keySet());

                progress.progress(0.60, "Checking which words are new…");
                int existing = countExisting(c, texts);
                int newWords = texts.size() - existing;

                progress.progress(0.65, "Saving " + texts.size() + " words…");
                upsertWords(c, counts, texts, progress);

                progress.progress(0.80, "Looking up word ids…");
                Map<String, Integer> ids = loadIds(c, texts);

                progress.progress(0.85, "Saving " + counts.bigramCount() + " word pairs…");
                upsertFollows(c, counts, ids, progress);

                progress.progress(0.98, "Recording the import…");
                int fileId = insertFile(c, fileName, filePath, sourceType, byteSize, counts, newWords,
                        (int) (System.currentTimeMillis() - startedAtMillis));
                c.commit();
                progress.progress(1.0, "Done");
                return new ImportedFile(fileId, fileName, filePath, sourceType, byteSize,
                        (int) counts.tokenCount(), (int) counts.sentenceCount(), counts.distinctWords(),
                        newWords, (int) (System.currentTimeMillis() - startedAtMillis), java.time.LocalDateTime.now());
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static int countExisting(Connection c, List<String> texts) throws SQLException {
        int found = 0;
        for (int from = 0; from < texts.size(); from += BATCH) {
            List<String> chunk = texts.subList(from, Math.min(texts.size(), from + BATCH));
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM word WHERE text IN (" + placeholders(chunk.size()) + ")")) {
                for (int i = 0; i < chunk.size(); i++) {
                    ps.setString(i + 1, chunk.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    found += rs.getInt(1);
                }
            }
        }
        return found;
    }

    private static void upsertWords(Connection c, CorpusCounts counts, List<String> texts,
                                    ProgressListener progress) throws SQLException {
        String sql = "INSERT INTO word (text, total_count, start_count, end_count) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE total_count = total_count + VALUES(total_count), "
                + "start_count = start_count + VALUES(start_count), end_count = end_count + VALUES(end_count)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int n = 0;
            for (String t : texts) {
                CorpusCounts.WordCounts wc = counts.words().get(t);
                ps.setString(1, t);
                ps.setInt(2, wc.total);
                ps.setInt(3, wc.start);
                ps.setInt(4, wc.end);
                ps.addBatch();
                if (++n % BATCH == 0) {
                    ps.executeBatch();
                    progress.progress(0.65 + 0.15 * n / texts.size(), "Saving words… " + n + "/" + texts.size());
                }
            }
            ps.executeBatch();
        }
    }

    private static Map<String, Integer> loadIds(Connection c, List<String> texts) throws SQLException {
        Map<String, Integer> ids = new HashMap<>(texts.size() * 2);
        for (int from = 0; from < texts.size(); from += BATCH) {
            List<String> chunk = texts.subList(from, Math.min(texts.size(), from + BATCH));
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT word_id, text FROM word WHERE text IN (" + placeholders(chunk.size()) + ")")) {
                for (int i = 0; i < chunk.size(); i++) {
                    ps.setString(i + 1, chunk.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.put(rs.getString(2), rs.getInt(1));
                    }
                }
            }
        }
        return ids;
    }

    private static void upsertFollows(Connection c, CorpusCounts counts, Map<String, Integer> ids,
                                      ProgressListener progress) throws SQLException {
        String sql = "INSERT INTO word_follow (word_id, next_word_id, count) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE count = count + VALUES(count)";
        int total = counts.bigramCount();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int n = 0;
            for (Map.Entry<String, Map<String, Integer>> e : counts.follows().entrySet()) {
                int wordId = idOf(ids, e.getKey());
                for (Map.Entry<String, Integer> f : e.getValue().entrySet()) {
                    ps.setInt(1, wordId);
                    ps.setInt(2, idOf(ids, f.getKey()));
                    ps.setInt(3, f.getValue());
                    ps.addBatch();
                    if (++n % BATCH == 0) {
                        ps.executeBatch();
                        progress.progress(0.85 + 0.13 * n / total, "Saving word pairs… " + n + "/" + total);
                    }
                }
            }
            ps.executeBatch();
        }
    }

    private static int insertFile(Connection c, String fileName, String filePath, ImportedFile.SourceType type,
                                  long byteSize, CorpusCounts counts, int newWords, int millis) throws SQLException {
        String sql = "INSERT INTO imported_file (file_name, file_path, source_type, byte_size, word_count, "
                + "sentence_count, distinct_words, new_words, import_millis) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fileName);
            ps.setString(2, filePath);
            ps.setString(3, type.name());
            ps.setLong(4, byteSize);
            ps.setLong(5, counts.tokenCount());
            ps.setLong(6, counts.sentenceCount());
            ps.setInt(7, counts.distinctWords());
            ps.setInt(8, newWords);
            ps.setInt(9, millis);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int idOf(Map<String, Integer> ids, String word) throws SQLException {
        Integer id = ids.get(word);
        if (id == null) {
            throw new SQLException("Word \"" + word + "\" was written but could not be read back. "
                    + "This usually means the word.text column is not using a binary collation (see db/schema.sql).");
        }
        return id;
    }

    static String placeholders(int n) {
        if (n == 0) {
            return "NULL";
        }
        StringBuilder sb = new StringBuilder("?");
        for (int i = 1; i < n; i++) {
            sb.append(",?");
        }
        return sb.toString();
    }
}
