package com.choirmanager.db;

import com.choirmanager.model.Member;
import com.choirmanager.model.Photo;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for photos and photo_tags tables.
 */
public class PhotoDAO {

    private final Connection conn;

    public PhotoDAO() throws SQLException {
        this.conn = DatabaseManager.getInstance().getConnection();
        ensureEmbeddingTable();
    }

    // -------------------------------------------------------------------------
    // Schema extension — face embeddings (not in initial schema)
    // -------------------------------------------------------------------------

    private void ensureEmbeddingTable() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS face_embeddings (
                    id             INTEGER PRIMARY KEY AUTOINCREMENT,
                    member_id      INTEGER NOT NULL REFERENCES members(id) ON DELETE CASCADE,
                    embedding      BLOB    NOT NULL,
                    source_photo_id INTEGER REFERENCES photos(id) ON DELETE SET NULL
                );
            """);
        }
    }

    // -------------------------------------------------------------------------
    // Photos — CRUD
    // -------------------------------------------------------------------------

    public Photo insertPhoto(Photo p) throws SQLException {
        String sql = """
            INSERT INTO photos (file_path, drive_file_id, event_id, taken_date, notes)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getFilePath());
            ps.setString(2, p.getDriveFileId());
            if (p.getEventId() != null) ps.setInt(3, p.getEventId()); else ps.setNull(3, Types.INTEGER);
            ps.setString(4, p.getTakenDate());
            ps.setString(5, p.getNotes());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) p.setId(keys.getInt(1));
            }
        }
        return p;
    }

    /** Returns the photo with the given local file path, or null if not found. */
    public Photo findByFilePath(String path) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM photos WHERE file_path = ?")) {
            ps.setString(1, path);
            List<Photo> r = mapResultSet(ps.executeQuery());
            return r.isEmpty() ? null : r.get(0);
        }
    }

    public List<Photo> findAll() throws SQLException {
        try (Statement s = conn.createStatement()) {
            return mapResultSet(s.executeQuery("SELECT * FROM photos ORDER BY taken_date DESC"));
        }
    }

    /** Returns all photos tagged with the given member. */
    public List<Photo> findByMember(int memberId) throws SQLException {
        String sql = """
            SELECT p.* FROM photos p
            JOIN photo_tags pt ON pt.photo_id = p.id
            WHERE pt.member_id = ?
            ORDER BY p.taken_date DESC
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            return mapResultSet(ps.executeQuery());
        }
    }

    public void deletePhoto(int photoId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM photos WHERE id=?")) {
            ps.setInt(1, photoId);
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Tags
    // -------------------------------------------------------------------------

    /** Tags a member in a photo. Silently ignores duplicate tags. */
    public void tagMember(int photoId, int memberId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO photo_tags (photo_id, member_id) VALUES (?, ?)")) {
            ps.setInt(1, photoId);
            ps.setInt(2, memberId);
            ps.executeUpdate();
        }
    }

    public void removeTag(int photoId, int memberId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM photo_tags WHERE photo_id=? AND member_id=?")) {
            ps.setInt(1, photoId);
            ps.setInt(2, memberId);
            ps.executeUpdate();
        }
    }

    public void removeAllTags(int photoId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM photo_tags WHERE photo_id=?")) {
            ps.setInt(1, photoId);
            ps.executeUpdate();
        }
    }

    public List<Member> getTaggedMembers(int photoId) throws SQLException {
        String sql = """
            SELECT m.* FROM members m
            JOIN photo_tags pt ON pt.member_id = m.id
            WHERE pt.photo_id = ?
            ORDER BY m.last_name, m.first_name
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, photoId);
            ResultSet rs = ps.executeQuery();
            List<Member> list = new ArrayList<>();
            while (rs.next()) {
                String vp = rs.getString("voice_part");
                list.add(new Member(
                    rs.getInt("id"), rs.getString("first_name"), rs.getString("last_name"),
                    rs.getString("email"), rs.getString("phone"),
                    vp != null ? Member.VoicePart.valueOf(vp) : null,
                    rs.getString("join_date"), rs.getInt("active") == 1, rs.getString("notes")
                ));
            }
            return list;
        }
    }

    // -------------------------------------------------------------------------
    // Face embeddings
    // -------------------------------------------------------------------------

    /** Stores a face embedding for a member (used for AI matching). */
    public void saveEmbedding(int memberId, float[] embedding, Integer sourcePhotoId) throws SQLException {
        // Each member can have multiple reference embeddings for robustness.
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO face_embeddings (member_id, embedding, source_photo_id) VALUES (?, ?, ?)")) {
            ps.setInt(1, memberId);
            ps.setBytes(2, floatsToBytes(embedding));
            if (sourcePhotoId != null) ps.setInt(3, sourcePhotoId); else ps.setNull(3, Types.INTEGER);
            ps.executeUpdate();
        }
    }

    /** Loads all stored embeddings. Returns list of [memberId, embedding] pairs. */
    public List<EmbeddingRecord> loadAllEmbeddings() throws SQLException {
        List<EmbeddingRecord> list = new ArrayList<>();
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT member_id, embedding FROM face_embeddings")) {
            while (rs.next()) {
                list.add(new EmbeddingRecord(
                    rs.getInt("member_id"),
                    bytesToFloats(rs.getBytes("embedding"))
                ));
            }
        }
        return list;
    }

    public record EmbeddingRecord(int memberId, float[] embedding) {}

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<Photo> mapResultSet(ResultSet rs) throws SQLException {
        List<Photo> list = new ArrayList<>();
        while (rs.next()) {
            int evId = rs.getInt("event_id");
            list.add(new Photo(
                rs.getInt("id"),
                rs.getString("file_path"),
                rs.getString("drive_file_id"),
                null, // folder name not stored in DB, resolved at runtime
                rs.wasNull() ? null : evId,
                rs.getString("taken_date"),
                rs.getString("notes")
            ));
        }
        return list;
    }

    private static byte[] floatsToBytes(float[] floats) {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(floats.length * 4);
        for (float f : floats) buf.putFloat(f);
        return buf.array();
    }

    private static float[] bytesToFloats(byte[] bytes) {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes);
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) floats[i] = buf.getFloat();
        return floats;
    }
}
