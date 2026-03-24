package com.hello.ecosortai;

/**
 * ScanRecord
 * Immutable data holder for a single waste classification result
 * kept in the in-session history list.
 */
public class ScanRecord {
    public final String label;
    /** Confidence in the range [0, 1]. */
    public final float confidence;
    /** Human-readable timestamp, e.g. "10:45 AM". */
    public final String timestamp;

    public ScanRecord(String label, float confidence, String timestamp) {
        this.label = label;
        this.confidence = confidence;
        this.timestamp = timestamp;
    }
}
