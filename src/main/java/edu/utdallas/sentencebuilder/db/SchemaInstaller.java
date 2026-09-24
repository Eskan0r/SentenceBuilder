package edu.utdallas.sentencebuilder.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs {@code db/schema.sql} against a connection.  Every statement in that
 * file is {@code CREATE ... IF NOT EXISTS}, so running it on every start-up is
 * harmless and means a fresh MySQL server needs no manual setup.
 * <p>
 * {@code CREATE DATABASE} / {@code USE} lines are skipped: the JDBC URL
 * already names the database (and creates it via createDatabaseIfNotExist),
 * so the user is free to point the app at a database with a different name.
 */
final class SchemaInstaller {

    private SchemaInstaller() {
    }

    static void install(Connection c) throws SQLException {
        String sql = readSchema();
        try (Statement st = c.createStatement()) {
            for (String stmt : splitStatements(sql)) {
                String head = stmt.toUpperCase(Locale.ROOT);
                if (head.startsWith("CREATE DATABASE") || head.startsWith("USE ")) {
                    continue;
                }
                st.execute(stmt);
            }
        }
    }

    private static String readSchema() throws SQLException {
        try (InputStream in = SchemaInstaller.class.getResourceAsStream("/db/schema.sql")) {
            if (in == null) {
                throw new SQLException("db/schema.sql not found on classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("Could not read db/schema.sql", e);
        }
    }

    /** Strips {@code -- comments} and splits on semicolons. Good enough for our own DDL file. */
    static List<String> splitStatements(String sql) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : sql.split("\n")) {
            int comment = line.indexOf("--");
            if (comment >= 0) {
                line = line.substring(0, comment);
            }
            cleaned.append(line).append('\n');
        }
        List<String> out = new ArrayList<>();
        for (String part : cleaned.toString().split(";")) {
            String s = part.trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }
}
