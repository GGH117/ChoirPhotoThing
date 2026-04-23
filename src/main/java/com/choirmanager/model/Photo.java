package com.choirmanager.model;
import java.util.*;
public class Photo {
    private int id; private String filePath,driveFileId,driveFolderName,takenDate,notes;
    private Integer eventId; private List<Member> taggedMembers=new ArrayList<>();
    public Photo(){}
    public Photo(int id,String filePath,String driveFileId,String driveFolderName,Integer eventId,String takenDate,String notes){
        this.id=id;this.filePath=filePath;this.driveFileId=driveFileId;this.driveFolderName=driveFolderName;this.eventId=eventId;this.takenDate=takenDate;this.notes=notes;}
    public int getId(){return id;} public void setId(int id){this.id=id;}
    public String getFilePath(){return filePath;} public void setFilePath(String v){filePath=v;}
    public String getDriveFileId(){return driveFileId;} public void setDriveFileId(String v){driveFileId=v;}
    public String getDriveFolderName(){return driveFolderName;} public void setDriveFolderName(String v){driveFolderName=v;}
    public Integer getEventId(){return eventId;} public void setEventId(Integer v){eventId=v;}
    public String getTakenDate(){return takenDate;} public void setTakenDate(String v){takenDate=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    public List<Member> getTaggedMembers(){return taggedMembers;} public void setTaggedMembers(List<Member> v){taggedMembers=v;}
    public String getDisplayName(){
        if(filePath!=null)return java.nio.file.Path.of(filePath).getFileName().toString();
        return driveFileId!=null?driveFileId:"Unknown";}
}
