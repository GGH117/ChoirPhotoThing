package com.choirmanager.model;

/**
 * A single face detected in a photo by the face recognition engine.
 * Holds the bounding box, the raw embedding vector, and any AI-suggested member match.
 */
public class DetectedFace {

    private final double x;          // normalized 0-1
    private final double y;
    private final double width;
    private final double height;
    private final float[] embedding; // 512-dim ArcFace embedding
    private Member suggestedMember;  // nullable — best AI match
    private double confidence;       // 0.0 - 1.0
    private Member confirmedMember;  // set after human confirmation

    public DetectedFace(double x, double y, double width, double height, float[] embedding) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.embedding = embedding;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public float[] getEmbedding() { return embedding; }

    public Member getSuggestedMember() { return suggestedMember; }
    public void setSuggestedMember(Member m) { this.suggestedMember = m; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public Member getConfirmedMember() { return confirmedMember; }
    public void setConfirmedMember(Member confirmedMember) { this.confirmedMember = confirmedMember; }

    /** Returns confirmed member if set, otherwise suggestion. */
    public Member getResolvedMember() {
        return confirmedMember != null ? confirmedMember : suggestedMember;
    }

    public boolean isConfirmed() { return confirmedMember != null; }

    @Override
    public String toString() {
        Member m = getResolvedMember();
        return m != null ? m.getFullName() : "Unknown";
    }
}
