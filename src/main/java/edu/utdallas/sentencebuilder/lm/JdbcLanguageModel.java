package edu.utdallas.sentencebuilder.lm;

import edu.utdallas.sentencebuilder.db.Database;
import edu.utdallas.sentencebuilder.model.Follower;
import edu.utdallas.sentencebuilder.model.WordStats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** {@link LanguageModel} that reads straight from MySQL.  No caching: edits in the Words screen take effect immediately. */
public final class JdbcLanguageModel implements LanguageModel {

    private final Database db;

    public JdbcLanguageModel(Database db) {
        this.db = db;
    }

    @Override
    public List<Follower> followers(String word) {
        String sql = "SELECT n.text, f.count, f.chosen_count FROM word_follow f "
                + "JOIN word w ON w.word_id = f.word_id JOIN word n ON n.word_id = f.next_word_id "
                + "WHERE w.text = ? AND (f.count + f.chosen_count) > 0 "
                + "ORDER BY (f.count + f.chosen_count) DESC, n.text";
        List<Follower> out = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, word);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Follower(rs.getString(1), rs.getInt(2), rs.getInt(3)));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load followers of \"" + word + "\"", e);
        }
        return out;
    }

    @Override
    public Optional<WordStats> stats(String word) {
        String sql = "SELECT total_count, start_count, end_count FROM word WHERE text = ?";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, word);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new WordStats(word, rs.getInt(1), rs.getInt(2), rs.getInt(3)));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load word \"" + word + "\"", e);
        }
    }

    @Override
    public Optional<String> randomStartWord(Random random) {
        try (Connection c = db.connection(); Statement st = c.createStatement()) {
            long total;
            try (ResultSet rs = st.executeQuery("SELECT COALESCE(SUM(start_count), 0) FROM word")) {
                rs.next();
                total = rs.getLong(1);
            }
            if (total == 0) {
                return Optional.empty();
            }
            long target = (long) (random.nextDouble() * total);
            // Walk the starters in id order until the running total passes the target.
            try (ResultSet rs = st.executeQuery(
                    "SELECT text, start_count FROM word WHERE start_count > 0 ORDER BY word_id")) {
                String last = null;
                while (rs.next()) {
                    last = rs.getString(1);
                    target -= rs.getLong(2);
                    if (target < 0) {
                        return Optional.of(last);
                    }
                }
                return Optional.ofNullable(last);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not pick a random start word", e);
        }
    }
}
