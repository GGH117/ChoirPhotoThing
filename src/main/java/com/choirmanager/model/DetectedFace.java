package com.choirmanager.model;
public class DetectedFace {
    private final double x,y,width,height; private final float[] embedding;
    private Member suggestedMember,confirmedMember; private double confidence;
    public DetectedFace(double x,double y,double width,double height,float[] embedding){
        this.x=x;this.y=y;this.width=width;this.height=height;this.embedding=embedding;}
    public double getX(){return x;} public double getY(){return y;}
    public double getWidth(){return width;} public double getHeight(){return height;}
    public float[] getEmbedding(){return embedding;}
    public Member getSuggestedMember(){return suggestedMember;} public void setSuggestedMember(Member m){suggestedMember=m;}
    public double getConfidence(){return confidence;} public void setConfidence(double v){confidence=v;}
    public Member getConfirmedMember(){return confirmedMember;} public void setConfirmedMember(Member m){confirmedMember=m;}
    public Member getResolvedMember(){return confirmedMember!=null?confirmedMember:suggestedMember;}
    public boolean isConfirmed(){return confirmedMember!=null;}
    @Override public String toString(){Member m=getResolvedMember();return m!=null?m.getFullName():"Unknown";}
}
