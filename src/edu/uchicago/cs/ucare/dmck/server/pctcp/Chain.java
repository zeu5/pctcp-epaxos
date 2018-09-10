package edu.uchicago.cs.ucare.dmck.server.pctcp;

import java.util.ArrayList;
import java.util.List;

public class Chain {

  private int id;
  private List<Node> elems;
  private int lastConsumedNode; // the index of the last consumed node

  public Chain(int id, List<Node> elems) {
    this.id = id;
    this.elems = elems;
    lastConsumedNode = -1;
  }

  public long getId() {
    return id;
  }

  public boolean append(Node elem) {
    if(canAppend(elem)) {
      elems.add(elem);
      return true;
    }

    return false;
  }

  // return the most recently added element in the chain
  public Node getMaximal() {
    if(elems.isEmpty()) return null;
    return elems.get(elems.size()-1);
  }

  public List<Node> getNodesToConsume() {
    List<Node> nodes = new ArrayList<Node>();
    for(int i = lastConsumedNode + 1; i < elems.size(); i++) {
      nodes.add(elems.get(i));
    }
    return nodes;
  }

  public boolean hasNodeToConsume() {
    return elems.size() > lastConsumedNode + 1;
  }

  public Node getNodeToConsume() {
    if(hasNodeToConsume()) {
      return elems.get(lastConsumedNode+1);
    }
    return null;
  }

  // marks the next element as consumed
  public Node consume() {
    if(hasNodeToConsume()) {
      lastConsumedNode ++;
      return elems.get(lastConsumedNode);
    }
    return null;
  }

  public Node getNode(int index) {
    if(index >= elems.size()) return null;
    return elems.get(index);
  }

  public boolean isConsumed(int index) {
    return lastConsumedNode >= index && index >= 0;
  }

  public String toString() {
    return concatNodeStrings(elems);
  }

  public String toConsumeToString() {
    return concatNodeStrings(getNodesToConsume());
  }

  private String concatNodeStrings(List<Node> nodes) {
    StringBuilder sb = new StringBuilder("Chain " + id + ": [ ");

    for(int i=0; i<nodes.size(); i++) {
      Node n = nodes.get(i);
      sb.append(n.getId());//.append("-").append(n.getTransition().getTransitionId());
      if(isConsumed(i)) sb.append("(C)");
      sb.append("  ");
    }
    return sb.append("]").toString();
  }

  //todo add hack for Cassandra - if the last transition is Paxos-Prepare and not executed, the next is concurrent to this - not appendable
  public boolean canAppend(Node elem) {

    // if the chain has a single node, which is not executed yet and it is a client request, then it is concurrent to the other messages
    //if(elems.size() == 1 && hasNodeToConsume() && elems.get(0).initiatedByClient()) return false;
    //if(elem.initiatedByClient()) return false;
    // if elems.get(elems.size() - 1).getEvent() happens before elem, returns 1
   // return VectorClockUtil.isConcurrent(elem.getEvent(), getMaximal().getEvent()) == 1 && !hasNodeToConsume();
    //  return VectorClockUtil.isConcurrent(elem.getEvent(), getMaximal().getEvent()) == 1 &&
    //    (!hasNodeToConsume() || Node.hasSameClientRequest(elem,  getMaximal()));
/*    return (
        (Node.hasSameSRPair(elem,  getMaximal()) &&
        (!hasNodeToConsume() || Node.hasSameClientRequest(elem,  getMaximal())))
        ||
        (Node.hasReverseSRPair(elem,  getMaximal()) && // the second is an answer
        (!hasNodeToConsume() || Node.hasSameClientRequest(elem,  getMaximal()))));
  */
    return
        (Node.hasSameSRPair(elem,  getMaximal()) && // same sender receiver AND
            // no node  to consume OR Same request..
            (!hasNodeToConsume() || Node.hasSameClientRequest(elem,  getMaximal())));
  
    //return !hasNodeToConsume(); 

 }


}
