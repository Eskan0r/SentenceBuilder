package edu.utdallas.sentencebuilder.repo;

import edu.utdallas.sentencebuilder.db.Database;
import edu.utdallas.sentencebuilder.model.ImportedFile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Read access to {@code imported_file}.  Rows are written by {@code CorpusWriter}. */
public final class ImportedFileRepository {

    private final Database db;

    public ImportedFileRepository(Database db) {
        this.db = db;
    }

    public List<ImportedFile> all() throws SQLException {
        String sql = "SELECT file_id, file_name, file_path, source_type, byte_size, word_count, sentence_count, "
                + "distinct_words, new_words, import_millis, imported_at FROM imported_file ORDER BY imported_at DESC, file_id DESC";
        List<ImportedFile> out = new ArrayList<>();
        try (Connection c = db.connection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp(11);
                out.add(new ImportedFile(rs.getInt(1), rs.getString(2), rs.getString(3),
                        ImportedFile.SourceType.valueOf(rs.getString(4)), rs.getLong(5), rs.getInt(6), rs.getInt(7),
                        rs.getInt(8), rs.getInt(9), rs.getInt(10), ts == null ? null : ts.toLocalDateTime()));
            }
        }
        return out;
    }

    /** Has a file with this exact path been imported before?  Used to warn about double imports. */
    public boolean wasImported(String path) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM imported_file WHERE file_path = ? LIMIT 1")) {
            ps.setString(1, path);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
