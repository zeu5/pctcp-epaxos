package edu.uchicago.cs.ucare.dmck.server.pctcp;

import edu.uchicago.cs.ucare.dmck.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ChainPartitioner {
  protected static Logger logger = LoggerFactory.getLogger(ChainPartitioner.class);

  private Partitioning partitioning = new Partitioning();

  public void insert(Transition transition) {
    if(!(transition instanceof PacketSendTransition)) {
      logger.error("Not supported this kind of message in PCT-ChainPartitioner yet!\n" + transition.toString());
      System.exit(-1);
    }
    String eventId = getEventId((PacketSendTransition) transition);
    Node n = new Node(eventId, transition);
    partitioning.insert(n);
  }

  // IMPORTANT: transition id hashes collide!
  // Instead of this, check a new transition with the not-executed transitions only (using hasCurrentTransitions)
  public boolean hasTransition(long transitionId) {
    return partitioning.hasTransition(transitionId);
  }

  public boolean hasCurrentTransition(long transitionId) {
    return partitioning.hasCurrentTransition(transitionId);
  }

  public List<Chain> getChains() {
    return partitioning.getChains();
  }

  public List<Long> getChainIds() {
    List<Chain> chains = getChains();
    List<Long> ids = new ArrayList<Long>();
    for(Chain c: chains)
      ids.add(c.getId());
    return ids;
  }

  public Chain getChainById(long chainId) {
    return partitioning.getChainById(chainId);
  }

  public Chain getChainByHeadEventId(String eventId) {
    return partitioning.getChainByHeadEventId(eventId);
  }

  public Chain getChainByHeadTransitionId(long transitionId) {
    return partitioning.getChainByHeadTransitionId(transitionId);
  }

  public static String getEventId(Transition t) {

    if(t instanceof PacketSendTransition) {
      PacketSendTransition pct = ((PacketSendTransition)t);
      StringBuilder sb = new StringBuilder("Req-");
      sb.append(pct.getPacket().getValue("clientRequest"));
      sb.append("--");
      sb.append(pct.getPacket().getValue("verb"));
      sb.append("--From-");
      sb.append(pct.getPacket().getValue("sendNode"));
      sb.append("--To-");
      sb.append(pct.getPacket().getValue("recvNode"));
      return sb.toString();
    } else {
      logger.error("Currently only provides ids for PacketSendTransitions\n");
      System.exit(-1);
    }
    return "NoEventIdAssigned";
  }

  @Override
  public String toString() {
    return partitioning.toString();
  }
}
