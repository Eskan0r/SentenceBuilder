package edu.utdallas.sentencebuilder.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Where the database lives and how to log in to it.
 * <p>
 * Two kinds of database are supported:
 * <ul>
 *   <li><b>MySQL / MariaDB</b> server (what the course asks for).</li>
 *   <li><b>Built-in</b>: an H2 database file under {@code ~/.sentencebuilder}
 *       running in MySQL-compatibility mode, so the same SQL works and nothing
 *       has to be installed.  Handy for development and demos.</li>
 * </ul>
 * Settings are resolved in this order, later sources winning:
 * <ol>
 *   <li>{@code db.properties} bundled on the classpath (defaults)</li>
 *   <li>{@code ~/.sentencebuilder/db.properties} (written by the Settings dialog)</li>
 *   <li>{@code -Dsb.db.url}, {@code -Dsb.db.user}, {@code -Dsb.db.password}</li>
 * </ol>
 */
public record DbSettings(String url, String user, String password) {

    public static final Path USER_DIR = Path.of(System.getProperty("user.home"), ".sentencebuilder");
    public static final Path USER_FILE = USER_DIR.resolve("db.properties");

    private static final String EMBEDDED_URL = "jdbc:h2:file:" + USER_DIR.resolve("sentence-builder")
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";

    /** The zero-setup option: a database file in the user's home directory. */
    public static DbSettings embedded() {
        return new DbSettings(EMBEDDED_URL, "sa", "");
    }

    public boolean isEmbedded() {
        return url.startsWith("jdbc:h2:");
    }

    /** True once the user has saved settings, i.e. this is not a first run. */
    public static boolean userFileExists() {
        return Files.isRegularFile(USER_FILE);
    }

    public static DbSettings load() {
        Properties props = new Properties();
        try (InputStream in = DbSettings.class.getResourceAsStream("/db.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
            // fall through to hard-coded defaults below
        }
        if (userFileExists()) {
            try (Reader r = Files.newBufferedReader(USER_FILE, StandardCharsets.UTF_8)) {
                props.load(r);
            } catch (IOException ignored) {
                // a broken user file should not stop the app from starting
            }
        }
        String url = System.getProperty("sb.db.url",
                props.getProperty("db.url", "jdbc:mysql://localhost:3306/sentence_builder?createDatabaseIfNotExist=true"));
        if ("embedded".equalsIgnoreCase(url)) {
            return embedded();
        }
        String user = System.getProperty("sb.db.user", props.getProperty("db.user", "root"));
        String password = System.getProperty("sb.db.password", props.getProperty("db.password", ""));
        return new DbSettings(url, user, password);
    }

    /** Persists these settings to {@link #USER_FILE} so they survive restarts. */
    public void save() throws IOException {
        Files.createDirectories(USER_DIR);
        Properties props = new Properties();
        props.setProperty("db.url", isEmbedded() ? "embedded" : url);
        props.setProperty("db.user", user);
        props.setProperty("db.password", password);
        try (Writer w = Files.newBufferedWriter(USER_FILE, StandardCharsets.UTF_8)) {
            props.store(w, "Sentence Builder database settings (db.url=embedded uses the built-in database)");
        }
    }

    /** Short description for the window title. */
    public String describe() {
        return isEmbedded() ? "built-in database (" + USER_DIR + ")" : url.replaceAll("\\?.*", "");
    }
}
