package com.choirmanager.db;

import com.choirmanager.model.Event;
import com.choirmanager.model.Event.EventType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Event CRUD operations.
 */
public class EventDAO {

    private final Connection conn;

    public EventDAO() throws SQLException {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    public Event insert(Event e) throws SQLException {
        String sql = """
            INSERT INTO events (title, event_date, start_time, end_time, location, event_type, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, e.getTitle());
            ps.setString(2, e.getEventDate());
            ps.setString(3, e.getStartTime());
            ps.setString(4, e.getEndTime());
            ps.setString(5, e.getLocation());
            ps.setString(6, e.getEventType() != null ? e.getEventType().name() : null);
            ps.setString(7, e.getNotes());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) e.setId(keys.getInt(1));
            }
        }
        return e;
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public List<Event> findAll() throws SQLException {
        return query("SELECT * FROM events ORDER BY event_date DESC, start_time DESC");
    }

    public List<Event> findByType(EventType type) throws SQLException {
        String sql = "SELECT * FROM events WHERE event_type = ? ORDER BY event_date DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type.name());
            return mapResultSet(ps.executeQuery());
        }
    }

    public Event findById(int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM events WHERE id = ?")) {
            ps.setInt(1, id);
            List<Event> results = mapResultSet(ps.executeQuery());
            return results.isEmpty() ? null : results.get(0);
        }
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    public void update(Event e) throws SQLException {
        String sql = """
            UPDATE events
            SET title=?, event_date=?, start_time=?, end_time=?, location=?, event_type=?, notes=?
            WHERE id=?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e.getTitle());
            ps.setString(2, e.getEventDate());
            ps.setString(3, e.getStartTime());
            ps.setString(4, e.getEndTime());
            ps.setString(5, e.getLocation());
            ps.setString(6, e.getEventType() != null ? e.getEventType().name() : null);
            ps.setString(7, e.getNotes());
            ps.setInt(8, e.getId());
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM events WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<Event> query(String sql) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return mapResultSet(rs);
        }
    }

    private List<Event> mapResultSet(ResultSet rs) throws SQLException {
        List<Event> list = new ArrayList<>();
        while (rs.next()) {
            String et = rs.getString("event_type");
            list.add(new Event(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getString("event_date"),
                rs.getString("start_time"),
                rs.getString("end_time"),
                rs.getString("location"),
                et != null ? EventType.valueOf(et) : null,
                rs.getString("notes")
            ));
        }
        return list;
    }
}
