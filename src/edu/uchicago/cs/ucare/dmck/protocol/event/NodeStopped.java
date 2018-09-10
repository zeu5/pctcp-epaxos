package edu.uchicago.cs.ucare.dmck.protocol.event;

// models node crashes/stops

import org.json.JSONException;
import org.json.JSONObject;

public class NodeStopped extends Event {
  private final String nodeId;

  public NodeStopped(long hashId, String nodeId) {
    super(hashId, EventType.NODE_STOPPED);
    this.nodeId = nodeId;
  }

  @Override
  public JSONObject toJson() {
    JSONObject obj = super.toJson();
    try {
      obj.put("nodeId", nodeId);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return obj;
  }
}
