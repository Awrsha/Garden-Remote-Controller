package com.example.smscontrol;

public class MessageItem {
    private String type;
    private String content;
    private String time;

    public MessageItem(String type, String content, String time) {
        this.type = type;
        this.content = content;
        this.time = time;
    }

    public String getType() { return type; }
    public String getContent() { return content; }
    public String getTime() { return time; }
}