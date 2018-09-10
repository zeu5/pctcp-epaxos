package edu.uchicago.cs.ucare.dmck.server.pctcp;

import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;

public class Node {

  private String messageId;
  private Transition transition;
 
  public Node(String messageId, Transition transition) {
    this.messageId = messageId;
    this.transition = transition;
  }

  public String getId() {
    return messageId;
  }

  public Transition getTransition() {
    return transition;
  }

  public Event getEvent() {
    if(transition instanceof PacketSendTransition) {
      return ((PacketSendTransition) transition).getPacket();
    }
    return null;
  }

  // todo eliminate application logic related code
  public boolean initiatedByClient() {
    Event e = getEvent();
    if(e != null && e.getValue("verb").equals("PAXOS_PREPARE")) 
      return true;

    return false;
  }

  // sample message-id: Req-1--PAXOS_COMMIT--From-1--To-2
  public static boolean hasSameClientRequest(Node n1, Node n2) {
    if(n1.messageId.length() < 5 || n2.messageId.length() < 5) {
      System.out.println("The message is not supported: " + n1.messageId + " " + n2.messageId);
      System.exit(-1);
    }
    if(n1.messageId.substring(0, 5).equals(n2.messageId.substring(0, 5)))
      return true;

    return false;
  }

  // sample message-id: Req-1--PAXOS_COMMIT--From-1--To-2
  public static boolean hasSameSRPair(Node n1, Node n2) {
    Transition t1 = n1.getTransition();
    Transition t2 = n2.getTransition();

    if(t1 instanceof PacketSendTransition && t2 instanceof PacketSendTransition) {
      return ((PacketSendTransition) t1).getPacket().getToId() == ((PacketSendTransition) t2).getPacket().getToId() &&
          ((PacketSendTransition) t1).getPacket().getFromId() == ((PacketSendTransition) t2).getPacket().getFromId();
    }

    return false;
  }
}
