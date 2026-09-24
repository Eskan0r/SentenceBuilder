package edu.utdallas.sentencebuilder.repo;

import edu.utdallas.sentencebuilder.db.Database;
import edu.utdallas.sentencebuilder.model.GeneratedSentence;
import edu.utdallas.sentencebuilder.text.Tokenizer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** History of everything the generators produced. */
public final class GeneratedSentenceRepository {

    private final Database db;

    public GeneratedSentenceRepository(Database db) {
        this.db = db;
    }

    /** Saves a sentence and returns a copy carrying its new id. */
    public GeneratedSentence save(GeneratedSentence s) throws SQLException {
        String sql = "INSERT INTO generated_sentence (sentence_text, sentence_hash, generator_name, start_word, "
                + "random_start, word_count) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.text());
            ps.setString(2, s.hash());
            ps.setString(3, s.generatorName());
            ps.setString(4, s.startWord());
            ps.setBoolean(5, s.randomStart());
            ps.setInt(6, s.words().size());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return s.withId(rs.getInt(1));
            }
        }
    }

    public void markFedBack(List<Integer> sentenceIds, int fileId) throws SQLException {
        if (sentenceIds.isEmpty()) {
            return;
        }
        String sql = "UPDATE generated_sentence SET fed_back_file_id = ? WHERE sentence_id = ?";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int id : sentenceIds) {
                ps.setInt(1, fileId);
                ps.setInt(2, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Sentence history, newest first, each row annotated with how many times
     * that exact text has been generated.
     *
     * @param duplicatesOnly only sentences generated more than once
     * @param textFilter     substring that must appear in the sentence (blank = all)
     */
    public List<GeneratedSentence> history(boolean duplicatesOnly, String textFilter, int limit) throws SQLException {
        String sql = "SELECT s.sentence_id, s.sentence_text, s.generator_name, s.start_word, s.random_start, "
                + "s.generated_at, s.fed_back_file_id, d.n "
                + "FROM generated_sentence s "
                + "JOIN (SELECT sentence_hash, COUNT(*) AS n FROM generated_sentence GROUP BY sentence_hash) d "
                + "  ON d.sentence_hash = s.sentence_hash "
                + "WHERE s.sentence_text LIKE ? " + (duplicatesOnly ? "AND d.n > 1 " : "")
                + "ORDER BY " + (duplicatesOnly ? "d.n DESC, s.sentence_hash, " : "") + "s.generated_at DESC, s.sentence_id DESC "
                + "LIMIT ?";
        List<GeneratedSentence> out = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "%" + (textFilter == null ? "" : textFilter.trim()) + "%");
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String text = rs.getString(2);
                    Integer fileId = rs.getObject(7) == null ? null : rs.getInt(7);
                    Timestamp ts = rs.getTimestamp(6);
                    out.add(new GeneratedSentence(rs.getInt(1), text, Tokenizer.words(text), rs.getString(3),
                            rs.getString(4), rs.getBoolean(5), ts == null ? null : ts.toLocalDateTime(), fileId,
                            rs.getInt(8)));
                }
            }
        }
        return out;
    }
}
