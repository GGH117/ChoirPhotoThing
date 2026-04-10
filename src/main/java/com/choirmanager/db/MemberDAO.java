package com.choirmanager.db;

import com.choirmanager.model.Member;
import com.choirmanager.model.Member.VoicePart;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Member CRUD operations.
 */
public class MemberDAO {

    private final Connection conn;

    public MemberDAO() throws SQLException {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    public Member insert(Member m) throws SQLException {
        String sql = """
            INSERT INTO members (first_name, last_name, email, phone, voice_part, join_date, active, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, m.getFirstName());
            ps.setString(2, m.getLastName());
            ps.setString(3, m.getEmail());
            ps.setString(4, m.getPhone());
            ps.setString(5, m.getVoicePart() != null ? m.getVoicePart().name() : null);
            ps.setString(6, m.getJoinDate());
            ps.setInt(7, m.isActive() ? 1 : 0);
            ps.setString(8, m.getNotes());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) m.setId(keys.getInt(1));
            }
        }
        return m;
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public List<Member> findAll() throws SQLException {
        return query("SELECT * FROM members ORDER BY last_name, first_name");
    }

    public List<Member> findActive() throws SQLException {
        return query("SELECT * FROM members WHERE active = 1 ORDER BY last_name, first_name");
    }

    public List<Member> findByVoicePart(VoicePart part) throws SQLException {
        String sql = "SELECT * FROM members WHERE voice_part = ? ORDER BY last_name, first_name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, part.name());
            return mapResultSet(ps.executeQuery());
        }
    }

    public Member findById(int id) throws SQLException {
        String sql = "SELECT * FROM members WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            List<Member> results = mapResultSet(ps.executeQuery());
            return results.isEmpty() ? null : results.get(0);
        }
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    public void update(Member m) throws SQLException {
        String sql = """
            UPDATE members
            SET first_name=?, last_name=?, email=?, phone=?, voice_part=?,
                join_date=?, active=?, notes=?
            WHERE id=?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.getFirstName());
            ps.setString(2, m.getLastName());
            ps.setString(3, m.getEmail());
            ps.setString(4, m.getPhone());
            ps.setString(5, m.getVoicePart() != null ? m.getVoicePart().name() : null);
            ps.setString(6, m.getJoinDate());
            ps.setInt(7, m.isActive() ? 1 : 0);
            ps.setString(8, m.getNotes());
            ps.setInt(9, m.getId());
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM members WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<Member> query(String sql) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return mapResultSet(rs);
        }
    }

    private List<Member> mapResultSet(ResultSet rs) throws SQLException {
        List<Member> list = new ArrayList<>();
        while (rs.next()) {
            String vp = rs.getString("voice_part");
            list.add(new Member(
                rs.getInt("id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("phone"),
                vp != null ? VoicePart.valueOf(vp) : null,
                rs.getString("join_date"),
                rs.getInt("active") == 1,
                rs.getString("notes")
            ));
        }
        return list;
    }
}
