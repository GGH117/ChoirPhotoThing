package com.choirmanager.model;

/**
 * Represents a choir member.
 */
public class Member {

    public enum VoicePart { Soprano, Alto, Tenor, Bass, Other }

    private int id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private VoicePart voicePart;
    private String joinDate;   // ISO-8601 string: "YYYY-MM-DD"
    private boolean active;
    private String notes;

    public Member() {}

    public Member(int id, String firstName, String lastName, String email,
                  String phone, VoicePart voicePart, String joinDate,
                  boolean active, String notes) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.voicePart = voicePart;
        this.joinDate = joinDate;
        this.active = active;
        this.notes = notes;
    }

    // Getters & setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getFullName() { return firstName + " " + lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public VoicePart getVoicePart() { return voicePart; }
    public void setVoicePart(VoicePart voicePart) { this.voicePart = voicePart; }

    public String getJoinDate() { return joinDate; }
    public void setJoinDate(String joinDate) { this.joinDate = joinDate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override
    public String toString() {
        return getFullName() + " (" + voicePart + ")";
    }
}
