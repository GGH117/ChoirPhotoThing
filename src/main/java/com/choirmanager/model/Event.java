package com.choirmanager.model;

/**
 * Represents a choir event (rehearsal, concert, workshop, etc.).
 */
public class Event {

    public enum EventType { Rehearsal, Concert, Workshop, Other }

    private int id;
    private String title;
    private String eventDate;   // "YYYY-MM-DD"
    private String startTime;   // "HH:MM"
    private String endTime;     // "HH:MM"
    private String location;
    private EventType eventType;
    private String notes;

    public Event() {}

    public Event(int id, String title, String eventDate, String startTime,
                 String endTime, String location, EventType eventType, String notes) {
        this.id = id;
        this.title = title;
        this.eventDate = eventDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.eventType = eventType;
        this.notes = notes;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getEventDate() { return eventDate; }
    public void setEventDate(String eventDate) { this.eventDate = eventDate; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override
    public String toString() {
        return eventDate + " — " + title + " [" + eventType + "]";
    }
}
