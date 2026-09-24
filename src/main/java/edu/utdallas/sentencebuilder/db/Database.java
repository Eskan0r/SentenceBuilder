package edu.utdallas.sentencebuilder.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.utdallas.sentencebuilder.config.DbSettings;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Owns the JDBC connection pool.  Everything that talks to MySQL goes through
 * {@link #connection()}; callers must close the connection (try-with-resources)
 * so it returns to the pool.
 */
public final class Database implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final DbSettings settings;

    /**
     * Opens the pool, verifies a connection can be made, and installs any
     * missing tables from {@code db/schema.sql}.
     */
    public Database(DbSettings settings) throws SQLException {
        this.settings = settings;
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(settings.url());
        // Name the driver explicitly rather than relying on ServiceLoader discovery,
        // which can be incomplete inside a shaded jar.
        cfg.setDriverClassName(settings.isEmbedded() ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver");
        cfg.setUsername(settings.user());
        cfg.setPassword(settings.password());
        cfg.setMaximumPoolSize(6);
        cfg.setPoolName("sentence-builder");
        cfg.setConnectionTimeout(8_000);
        cfg.setInitializationFailTimeout(-1); // don't block; we test below
        this.dataSource = new HikariDataSource(cfg);
        try (Connection c = dataSource.getConnection()) {
            SchemaInstaller.install(c);
        } catch (SQLException e) {
            dataSource.close();
            throw e;
        }
    }

    public DbSettings settings() {
        return settings;
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
