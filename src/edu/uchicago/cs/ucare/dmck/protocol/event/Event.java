package edu.uchicago.cs.ucare.dmck.protocol.event;

import org.json.JSONException;
import org.json.JSONObject;

// These are the formats to be recorded / transmitted over the network for visualization
public abstract class Event {

  public enum EventType {
    NODE_STARTED, NODE_STOPPED, MESSAGE_SENT, MESSAGE_RECEIVED, MESSAGE_DROPPED
  }

  public final EventType eventType;
  private long hashId = 0; // hashId used by the DMCK model checker

  public Event(long hashId, EventType eventType) {
    this.hashId = hashId;
    this.eventType = eventType;
  }

  public long getHashId() {
    return hashId;
  }

  public JSONObject toJson() {
    JSONObject json = new JSONObject();
    try {
      json.put("hashId", hashId);
      json.put("eventType", eventType);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return json;
  }
  
  // true of the hashId assigned by the model checker is the same along with the type
  public boolean equals(Event other) {
    return this.eventType == other.eventType && this.hashId == other.hashId;
  }
}

