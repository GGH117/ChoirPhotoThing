package com.choirmanager.db;

import com.choirmanager.model.AttendanceRecord;
import com.choirmanager.model.AttendanceRecord.Status;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for attendance records.
 */
public class AttendanceDAO {

    private final Connection conn;

    public AttendanceDAO() throws SQLException {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    // -------------------------------------------------------------------------
    // Upsert (insert or update if record already exists)
    // -------------------------------------------------------------------------

    public void upsert(AttendanceRecord r) throws SQLException {
        String sql = """
            INSERT INTO attendance (member_id, event_id, status, notes)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(member_id, event_id) DO UPDATE SET
                status = excluded.status,
                notes  = excluded.notes
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, r.getMemberId());
            ps.setInt(2, r.getEventId());
            ps.setString(3, r.getStatus().name());
            ps.setString(4, r.getNotes());
            ps.executeUpdate();
        }
    }

    /**
     * Saves a full list of attendance records for one event in a single transaction.
     */
    public void saveForEvent(int eventId, List<AttendanceRecord> records) throws SQLException {
        conn.setAutoCommit(false);
        try {
            for (AttendanceRecord r : records) {
                r.setEventId(eventId);
                upsert(r);
            }
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns all attendance records for an event, joined with member info.
     * Active members not yet recorded are returned with status = null.
     */
    public List<AttendanceRecord> findForEvent(int eventId) throws SQLException {
        String sql = """
            SELECT
                m.id          AS member_id,
                m.first_name || ' ' || m.last_name AS member_name,
                m.voice_part,
                COALESCE(a.id, 0)          AS rec_id,
                a.status,
                a.notes
            FROM members m
            LEFT JOIN attendance a
                ON a.member_id = m.id AND a.event_id = ?
            WHERE m.active = 1
            ORDER BY m.last_name, m.first_name
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ResultSet rs = ps.executeQuery();
            List<AttendanceRecord> list = new ArrayList<>();
            while (rs.next()) {
                String statusStr = rs.getString("status");
                list.add(new AttendanceRecord(
                    rs.getInt("rec_id"),
                    rs.getInt("member_id"),
                    eventId,
                    rs.getString("member_name"),
                    rs.getString("voice_part"),
                    statusStr != null ? Status.valueOf(statusStr) : null,
                    rs.getString("notes")
                ));
            }
            return list;
        }
    }

    /**
     * Returns attendance summary for a member across all events:
     * counts per status.
     */
    public AttendanceSummary getSummaryForMember(int memberId) throws SQLException {
        String sql = """
            SELECT status, COUNT(*) AS cnt
            FROM attendance
            WHERE member_id = ?
            GROUP BY status
        """;
        AttendanceSummary summary = new AttendanceSummary();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String s = rs.getString("status");
                int cnt = rs.getInt("cnt");
                switch (s) {
                    case "Present" -> summary.present = cnt;
                    case "Absent"  -> summary.absent  = cnt;
                    case "Excused" -> summary.excused  = cnt;
                    case "Late"    -> summary.late     = cnt;
                }
            }
        }
        return summary;
    }

    public record AttendanceSummary(int present, int absent, int excused, int late) {
        public AttendanceSummary() { this(0, 0, 0, 0); }
        public int total() { return present + absent + excused + late; }
        public String rate() {
            if (total() == 0) return "N/A";
            return String.format("%.0f%%", (present + late) * 100.0 / total());
        }
    }
}
