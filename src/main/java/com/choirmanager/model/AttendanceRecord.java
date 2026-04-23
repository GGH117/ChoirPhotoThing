package com.choirmanager.model;
public class AttendanceRecord {
    public enum Status { Present, Absent, Excused, Late }
    private int id,memberId,eventId; private String memberName,voicePart,notes; private Status status;
    public AttendanceRecord(){}
    public AttendanceRecord(int id,int memberId,int eventId,String memberName,String voicePart,Status status,String notes){
        this.id=id;this.memberId=memberId;this.eventId=eventId;this.memberName=memberName;this.voicePart=voicePart;this.status=status;this.notes=notes;}
    public int getId(){return id;} public void setId(int id){this.id=id;}
    public int getMemberId(){return memberId;} public void setMemberId(int v){memberId=v;}
    public int getEventId(){return eventId;} public void setEventId(int v){eventId=v;}
    public String getMemberName(){return memberName;} public void setMemberName(String v){memberName=v;}
    public String getVoicePart(){return voicePart;} public void setVoicePart(String v){voicePart=v;}
    public Status getStatus(){return status;} public void setStatus(Status v){status=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
}
