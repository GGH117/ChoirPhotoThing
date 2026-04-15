package com.choirmanager.model;

/**
 * Represents a single attendance record: one member at one event.
 */
public class AttendanceRecord {

    public enum Status { Present, Absent, Excused, Late }

    private int id;
    private int memberId;
    private int eventId;
    private String memberName;   // denormalized for display
    private String voicePart;    // denormalized for display
    private Status status;
    private String notes;

    public AttendanceRecord() {}

    public AttendanceRecord(int id, int memberId, int eventId,
                            String memberName, String voicePart,
                            Status status, String notes) {
        this.id = id;
        this.memberId = memberId;
        this.eventId = eventId;
        this.memberName = memberName;
        this.voicePart = voicePart;
        this.status = status;
        this.notes = notes;
    }

    public int getId()             { return id; }
    public void setId(int id)      { this.id = id; }

    public int getMemberId()               { return memberId; }
    public void setMemberId(int memberId)  { this.memberId = memberId; }

    public int getEventId()                { return eventId; }
    public void setEventId(int eventId)    { this.eventId = eventId; }

    public String getMemberName()                  { return memberName; }
    public void setMemberName(String memberName)   { this.memberName = memberName; }

    public String getVoicePart()               { return voicePart; }
    public void setVoicePart(String voicePart) { this.voicePart = voicePart; }

    public Status getStatus()              { return status; }
    public void setStatus(Status status)   { this.status = status; }

    public String getNotes()               { return notes; }
    public void setNotes(String notes)     { this.notes = notes; }
}
