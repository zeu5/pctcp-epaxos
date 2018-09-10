package edu.uchicago.cs.ucare.dmck.server.pctcp;

import edu.uchicago.cs.ucare.dmck.util.VectorClockUtil;

import java.util.*;

public class BSet {
  private List<Chain> chains;
  private int capacity;

  public BSet(int capacity) {
    chains = new ArrayList<>();
    this.capacity = capacity;
  }

  public BSet(int capacity, Chain c) {
    chains = new ArrayList<>();
    chains.add(c);
    this.capacity = capacity;
  }

  public BSet(int capacity, List<Chain> chains) {
    this.chains = chains;
    this.capacity = capacity;
  }

  public BSet(List<Chain> c) {
    chains = c;
  }

  public List<Chain> getChains() {
    return chains;
  }

  public int getCapacity() {
    return capacity;
  }

  public int getNumChains() {
    return chains.size();
  }


  public int size() {
    return chains.size();
  }

  public List<Chain> getAppendableChains(Node node) {
    List<Chain> appendable = new ArrayList<Chain>();

    for(Chain c: chains)
      if(c.canAppend(node)) appendable.add(c);

    return appendable;
  }

  public void addChain(Chain c) {
    chains.add(c);
    assert(chains.size() <= capacity);
  }

  public void removeChain(Chain c) {
    chains.remove(c);
  }

  public boolean hasComparableMaximals() {
    List<Node> heads = new ArrayList<Node>();

    for(Chain c: chains) {
      Node head = c.getMaximal();
      if(head != null) heads.add(head);
    }

    for(int i = 0; i < heads.size(); i++) {
      for(int j = 0; j < i; j++) {
        if(VectorClockUtil.isConcurrent(heads.get(i).getEvent(), heads.get(j).getEvent()) != 0)
          return true;
      }
    }
    return false;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    for(Chain c: chains) {
      sb.append(c.toString());
      sb.append("\n");
    }
    return sb.toString();
  }
}
