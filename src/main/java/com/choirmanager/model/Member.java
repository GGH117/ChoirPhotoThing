package com.choirmanager.model;
public class Member {
    public enum VoicePart { Soprano, Alto, Tenor, Bass, Other }
    private int id; private String firstName, lastName, email, phone, joinDate, notes;
    private VoicePart voicePart; private boolean active;
    public Member() {}
    public Member(int id,String firstName,String lastName,String email,String phone,VoicePart voicePart,String joinDate,boolean active,String notes){
        this.id=id;this.firstName=firstName;this.lastName=lastName;this.email=email;this.phone=phone;this.voicePart=voicePart;this.joinDate=joinDate;this.active=active;this.notes=notes;}
    public int getId(){return id;} public void setId(int id){this.id=id;}
    public String getFirstName(){return firstName;} public void setFirstName(String v){firstName=v;}
    public String getLastName(){return lastName;} public void setLastName(String v){lastName=v;}
    public String getFullName(){return firstName+" "+lastName;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getPhone(){return phone;} public void setPhone(String v){phone=v;}
    public VoicePart getVoicePart(){return voicePart;} public void setVoicePart(VoicePart v){voicePart=v;}
    public String getJoinDate(){return joinDate;} public void setJoinDate(String v){joinDate=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    @Override public String toString(){return getFullName()+" ("+voicePart+")";}
}
