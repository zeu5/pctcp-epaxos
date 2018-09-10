package edu.uchicago.cs.ucare.dmck.protocol.event;

import org.json.JSONException;
import org.json.JSONObject;

public class MessageSent extends Event {
  private String senderId;
  private String receiverId;
  private String content;

  public MessageSent(long hashId, String senderId, String receiverId, String msgContent) {
    super(hashId, EventType.MESSAGE_SENT);
    this.senderId = senderId;
    this.receiverId = receiverId;
    this.content = msgContent;
  }

  public String getSenderId() {
    return senderId;
  }

  public void setSenderId(String senderId) {
    this.senderId = senderId;
  }

  public String getReceiverId() {
    return receiverId;
  }

  public void setReceiverId(String receiverId) {
    this.receiverId = receiverId;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public JSONObject toJson() {
    JSONObject obj = super.toJson();
    try {
      obj.put("senderId", senderId);
      obj.put("receiverId", receiverId);
      obj.put("content", content);

    } catch (JSONException e) {
      e.printStackTrace();
    }
    return obj;
  }

}
