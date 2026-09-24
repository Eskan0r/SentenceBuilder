package edu.utdallas.sentencebuilder.autocomplete;

import edu.utdallas.sentencebuilder.db.Database;
import edu.utdallas.sentencebuilder.lm.LanguageModel;
import edu.utdallas.sentencebuilder.model.Follower;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Backs the Auto-complete screen.
 * <p>
 * Suggestions are the followers of the previous word ranked by corpus count
 * plus how often users have chosen them here.  Every word the user commits
 * (by typing a space, comma or period after it) is written straight to the
 * database: unknown words are added, known words get their counts bumped, and
 * the (previous, word) pair gets both {@code count} and {@code chosen_count}
 * incremented so the model learns from the user.
 */
public final class AutoCompleteService {

    private final Database db;
    private final LanguageModel model;

    public AutoCompleteService(Database db, LanguageModel model) {
        this.db = db;
        this.model = model;
    }

    public List<Follower> suggestions(String previousWord, int limit) {
        List<Follower> all = model.followers(previousWord);
        return all.size() <= limit ? all : all.subList(0, limit);
    }

    /**
     * Records that {@code word} was typed/chosen after {@code previousWord}
     * ({@code null} when it starts a sentence).
     *
     * @return true if the word was not in the database before
     */
    public boolean recordWord(String previousWord, String word) throws SQLException {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                boolean isNew = !exists(c, word);
                int startInc = previousWord == null ? 1 : 0;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO word (text, total_count, start_count) VALUES (?, 1, ?) "
                        + "ON DUPLICATE KEY UPDATE total_count = total_count + 1, start_count = start_count + VALUES(start_count)")) {
                    ps.setString(1, word);
                    ps.setInt(2, startInc);
                    ps.executeUpdate();
                }
                if (previousWord != null) {
                    int prevId = idOf(c, previousWord);
                    int wordId = idOf(c, word);
                    if (prevId > 0 && wordId > 0) {
                        try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO word_follow (word_id, next_word_id, count, chosen_count) VALUES (?, ?, 1, 1) "
                                + "ON DUPLICATE KEY UPDATE count = count + 1, chosen_count = chosen_count + 1")) {
                            ps.setInt(1, prevId);
                            ps.setInt(2, wordId);
                            ps.executeUpdate();
                        }
                    }
                }
                c.commit();
                return isNew;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** The user typed a period after {@code word}: it ended a sentence. */
    public void recordSentenceEnd(String word) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE word SET end_count = end_count + 1 WHERE text = ?")) {
            ps.setString(1, word);
            ps.executeUpdate();
        }
    }

    /** Logs a finished auto-complete session in {@code imported_file} so it shows up in the import history. */
    public void recordSession(int words, int sentences, int newWords) throws SQLException {
        String sql = "INSERT INTO imported_file (file_name, source_type, byte_size, word_count, sentence_count, distinct_words, new_words) "
                + "VALUES (?, 'AUTOCOMPLETE', 0, ?, ?, 0, ?)";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "auto-complete session");
            ps.setInt(2, words);
            ps.setInt(3, sentences);
            ps.setInt(4, newWords);
            ps.executeUpdate();
        }
    }

    private static boolean exists(Connection c, String word) throws SQLException {
        return idOf(c, word) > 0;
    }

    /** The word's id, or 0 if it is not in the database. */
    private static int idOf(Connection c, String word) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT word_id FROM word WHERE text = ?")) {
            ps.setString(1, word);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
