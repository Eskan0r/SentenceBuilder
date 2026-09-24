package edu.utdallas.sentencebuilder.repo;

import edu.utdallas.sentencebuilder.db.Database;
import edu.utdallas.sentencebuilder.model.CorpusSummary;
import edu.utdallas.sentencebuilder.model.FollowerRow;
import edu.utdallas.sentencebuilder.model.WordRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Browsing and editing of the {@code word} and {@code word_follow} tables. */
public final class WordRepository {

    /** Sort orders offered by the Words screen. */
    public enum Sort {
        ALPHABETICAL("Alphabetical", "w.text ASC"),
        MOST_FREQUENT("Most frequent", "w.total_count DESC, w.text ASC"),
        LEAST_FREQUENT("Least frequent", "w.total_count ASC, w.text ASC"),
        SENTENCE_STARTERS("Starts sentences most", "w.start_count DESC, w.text ASC"),
        SENTENCE_ENDERS("Ends sentences most", "w.end_count DESC, w.text ASC"),
        MOST_FOLLOWERS("Most distinct followers", "follower_count DESC, w.text ASC"),
        LONGEST("Longest", "CHAR_LENGTH(w.text) DESC, w.text ASC");

        public final String label;
        final String orderBy;

        Sort(String label, String orderBy) {
            this.label = label;
            this.orderBy = orderBy;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Database db;

    public WordRepository(Database db) {
        this.db = db;
    }

    /**
     * Lists words.
     *
     * @param filter   prefix filter; {@code *ing} style patterns are allowed ({@code *} = anything)
     * @param minTotal only words seen at least this many times
     * @param sort     ordering
     * @param limit    maximum rows
     */
    public List<WordRow> search(String filter, int minTotal, Sort sort, int limit) throws SQLException {
        String like;
        if (filter == null || filter.isBlank()) {
            like = "%";
        } else if (filter.contains("*")) {
            like = filter.trim().toLowerCase().replace('*', '%');   // explicit pattern, e.g. *ing or th*ng
        } else {
            like = filter.trim().toLowerCase() + "%";               // plain text = prefix match
        }
        String sql = "SELECT w.word_id, w.text, w.total_count, w.start_count, w.end_count, "
                + "(SELECT COUNT(*) FROM word_follow f WHERE f.word_id = w.word_id) AS follower_count "
                + "FROM word w WHERE w.text LIKE ? AND w.total_count >= ? ORDER BY " + sort.orderBy + " LIMIT ?";
        List<WordRow> out = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setInt(2, minTotal);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rowOf(rs));
                }
            }
        }
        return out;
    }

    public Optional<WordRow> find(String text) throws SQLException {
        String sql = "SELECT w.word_id, w.text, w.total_count, w.start_count, w.end_count, "
                + "(SELECT COUNT(*) FROM word_follow f WHERE f.word_id = w.word_id) AS follower_count "
                + "FROM word w WHERE w.text = ?";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, text);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rowOf(rs)) : Optional.empty();
            }
        }
    }

    public List<FollowerRow> followers(int wordId) throws SQLException {
        String sql = "SELECT f.word_id, f.next_word_id, n.text, f.count, f.chosen_count FROM word_follow f "
                + "JOIN word n ON n.word_id = f.next_word_id WHERE f.word_id = ? "
                + "ORDER BY (f.count + f.chosen_count) DESC, n.text";
        List<FollowerRow> out = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, wordId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new FollowerRow(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getInt(4), rs.getInt(5)));
                }
            }
        }
        return out;
    }

    /** Which words does {@code wordId} follow?  (The reverse of {@link #followers}.) */
    public List<FollowerRow> predecessors(int wordId) throws SQLException {
        String sql = "SELECT f.word_id, f.next_word_id, p.text, f.count, f.chosen_count FROM word_follow f "
                + "JOIN word p ON p.word_id = f.word_id WHERE f.next_word_id = ? "
                + "ORDER BY (f.count + f.chosen_count) DESC, p.text";
        List<FollowerRow> out = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, wordId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new FollowerRow(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getInt(4), rs.getInt(5)));
                }
            }
        }
        return out;
    }

    public void updateCounts(int wordId, int total, int start, int end) throws SQLException {
        String sql = "UPDATE word SET total_count = ?, start_count = ?, end_count = ? WHERE word_id = ?";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(0, total));
            ps.setInt(2, Math.max(0, start));
            ps.setInt(3, Math.max(0, end));
            ps.setInt(4, wordId);
            ps.executeUpdate();
        }
    }

    public void updateFollowCount(int wordId, int nextWordId, int count) throws SQLException {
        String sql = "UPDATE word_follow SET count = ? WHERE word_id = ? AND next_word_id = ?";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(0, count));
            ps.setInt(2, wordId);
            ps.setInt(3, nextWordId);
            ps.executeUpdate();
        }
    }

    public CorpusSummary summary() throws SQLException {
        try (Connection c = db.connection(); Statement st = c.createStatement()) {
            int words = 0;
            long tokens = 0;
            long starts = 0;
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*), COALESCE(SUM(total_count),0), COALESCE(SUM(start_count),0) FROM word")) {
                if (rs.next()) {
                    words = rs.getInt(1);
                    tokens = rs.getLong(2);
                    starts = rs.getLong(3);
                }
            }
            int bigrams = scalar(st, "SELECT COUNT(*) FROM word_follow");
            int files = scalar(st, "SELECT COUNT(*) FROM imported_file");
            int sentences = scalar(st, "SELECT COUNT(*) FROM generated_sentence");
            int distinct = scalar(st, "SELECT COUNT(DISTINCT sentence_hash) FROM generated_sentence");
            return new CorpusSummary(words, tokens, starts, bigrams, files, sentences, distinct);
        }
    }

    private static int scalar(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static WordRow rowOf(ResultSet rs) throws SQLException {
        return new WordRow(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6));
    }
}
