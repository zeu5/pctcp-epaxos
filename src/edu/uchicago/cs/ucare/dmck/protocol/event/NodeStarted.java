package edu.uchicago.cs.ucare.dmck.protocol.event;

import org.json.JSONException;
import org.json.JSONObject;

public class NodeStarted extends Event {
  private final String nodeId;

  public NodeStarted(long hashId, String nodeId) {
    super(hashId, EventType.NODE_STARTED);
    this.nodeId = nodeId;
  }

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
