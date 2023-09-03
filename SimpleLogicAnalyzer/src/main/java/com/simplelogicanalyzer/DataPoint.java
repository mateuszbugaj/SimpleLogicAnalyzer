package com.simplelogicanalyzer;

public class DataPoint {
    public String content;
    public long timestamp;
    public int chartXPosition = 0;

    public DataPoint(String content, long timestamp) {
        this.content = content;
        this.timestamp = timestamp;
    }
}
