package com.choirmanager.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a photo — may live on disk, in Google Drive, or both.
 */
public class Photo {

    private int id;
    private String filePath;       // local cache path
    private String driveFileId;    // Google Drive file ID
    private String driveFolderName;// e.g. "2024-03-15 Spring Concert"
    private Integer eventId;       // nullable FK to events table
    private String takenDate;      // "YYYY-MM-DD"
    private String notes;
    private List<Member> taggedMembers = new ArrayList<>();

    public Photo() {}

    public Photo(int id, String filePath, String driveFileId, String driveFolderName,
                 Integer eventId, String takenDate, String notes) {
        this.id = id;
        this.filePath = filePath;
        this.driveFileId = driveFileId;
        this.driveFolderName = driveFolderName;
        this.eventId = eventId;
        this.takenDate = takenDate;
        this.notes = notes;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getDriveFileId() { return driveFileId; }
    public void setDriveFileId(String driveFileId) { this.driveFileId = driveFileId; }

    public String getDriveFolderName() { return driveFolderName; }
    public void setDriveFolderName(String driveFolderName) { this.driveFolderName = driveFolderName; }

    public Integer getEventId() { return eventId; }
    public void setEventId(Integer eventId) { this.eventId = eventId; }

    public String getTakenDate() { return takenDate; }
    public void setTakenDate(String takenDate) { this.takenDate = takenDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<Member> getTaggedMembers() { return taggedMembers; }
    public void setTaggedMembers(List<Member> taggedMembers) { this.taggedMembers = taggedMembers; }

    /** Display label for the UI. */
    public String getDisplayName() {
        if (filePath != null) {
            return java.nio.file.Path.of(filePath).getFileName().toString();
        }
        return driveFileId != null ? driveFileId : "Unknown";
    }
}
