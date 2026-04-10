package com.choirmanager.db;

import java.sql.*;

/**
 * Manages the SQLite database connection and schema initialization.
 * Single-instance (singleton) used throughout the app.
 */
public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:choir_manager.db";
    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() throws SQLException {
        connection = DriverManager.getConnection(DB_URL);
        connection.createStatement().execute("PRAGMA foreign_keys = ON;");
        initializeSchema();
    }

    public static DatabaseManager getInstance() throws SQLException {
        if (instance == null || instance.connection.isClosed()) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    // -------------------------------------------------------------------------
    // Schema
    // -------------------------------------------------------------------------

    private void initializeSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // Members
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS members (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    first_name  TEXT NOT NULL,
                    last_name   TEXT NOT NULL,
                    email       TEXT,
                    phone       TEXT,
                    voice_part  TEXT CHECK(voice_part IN ('Soprano','Alto','Tenor','Bass','Other')),
                    join_date   TEXT,
                    active      INTEGER NOT NULL DEFAULT 1,
                    notes       TEXT
                );
            """);

            // Events (rehearsals, concerts, etc.)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS events (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    title       TEXT NOT NULL,
                    event_date  TEXT NOT NULL,
                    start_time  TEXT,
                    end_time    TEXT,
                    location    TEXT,
                    event_type  TEXT CHECK(event_type IN ('Rehearsal','Concert','Workshop','Other')),
                    notes       TEXT
                );
            """);

            // Attendance
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS attendance (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    member_id INTEGER NOT NULL REFERENCES members(id) ON DELETE CASCADE,
                    event_id  INTEGER NOT NULL REFERENCES events(id) ON DELETE CASCADE,
                    status    TEXT NOT NULL CHECK(status IN ('Present','Absent','Excused','Late')),
                    notes     TEXT,
                    UNIQUE(member_id, event_id)
                );
            """);

            // Photos
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS photos (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_path     TEXT NOT NULL,
                    drive_file_id TEXT,
                    event_id      INTEGER REFERENCES events(id) ON DELETE SET NULL,
                    taken_date    TEXT,
                    notes         TEXT
                );
            """);

            // Photo tags (which members appear in which photo)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS photo_tags (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    photo_id  INTEGER NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
                    member_id INTEGER NOT NULL REFERENCES members(id) ON DELETE CASCADE,
                    UNIQUE(photo_id, member_id)
                );
            """);
        }
    }
}
