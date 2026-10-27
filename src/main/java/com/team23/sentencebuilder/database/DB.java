package com.team23.sentencebuilder.database;

import io.github.cdimascio.dotenv.Dotenv;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DB {

    private static final Dotenv env = Dotenv.load();

    public static final String host = env.get("DB_HOST");
    public static final String port = env.get("DB_PORT", "3306");
    public static final String user = env.get("DB_USER");
    public static final String dbName = env.get("DB_NAME");
    public static final String password = env.get("DB_PASSWORD");

    public static final String URL = "jdbc:mysql://" + host + ":" + port + "/" + dbName;

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, user, password);
    }

    public static void main(String[] args) {
        try (Connection conn = getConnection()) {
            System.out.println("Connected: " + conn.getMetaData().getURL());
        } catch (SQLException e) {
            System.out.println("Connection failed: " + e.getMessage());
        }
    }
}
