package com.choirmanager.model;
public class Event {
    public enum EventType { Rehearsal, Concert, Workshop, Other }
    private int id; private String title,eventDate,startTime,endTime,location,notes; private EventType eventType;
    public Event(){}
    public Event(int id,String title,String eventDate,String startTime,String endTime,String location,EventType eventType,String notes){
        this.id=id;this.title=title;this.eventDate=eventDate;this.startTime=startTime;this.endTime=endTime;this.location=location;this.eventType=eventType;this.notes=notes;}
    public int getId(){return id;} public void setId(int id){this.id=id;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getEventDate(){return eventDate;} public void setEventDate(String v){eventDate=v;}
    public String getStartTime(){return startTime;} public void setStartTime(String v){startTime=v;}
    public String getEndTime(){return endTime;} public void setEndTime(String v){endTime=v;}
    public String getLocation(){return location;} public void setLocation(String v){location=v;}
    public EventType getEventType(){return eventType;} public void setEventType(EventType v){eventType=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    @Override public String toString(){return eventDate+" — "+title+" ["+eventType+"]";}
}
