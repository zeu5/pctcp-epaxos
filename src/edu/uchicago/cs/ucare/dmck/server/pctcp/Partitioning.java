package edu.uchicago.cs.ucare.dmck.server.pctcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

// Partitioning = List<BSET>
public class Partitioning {
  protected static Logger logger = LoggerFactory.getLogger("Partitioning");

  // chains are composed of nodes - an event id and event - each event keeps its own a vector clock info
  private int chainId = 0;

  private List<BSet> partitions;
  private int numBSets = 0;

  private Set<Long> transitionIds = new HashSet<Long>();

  private int numAdded = 0;
  private Map<String, Integer> map = new HashMap<String, Integer>();  
  public Partitioning() {
    partitions = new ArrayList<>();
  }

  public void insert(Node node) {
    map.put(node.getId(), ++numAdded);
    insertIntoBSet(0, node);
    transitionIds.add(node.getTransition().getTransitionId());

    for(int i = 0; i< partitions.size(); i++) {
      assert (partitions.get(i).size() <= i + 1);
      assert (!partitions.get(i).hasComparableMaximals());
    }
  }

  private void insertIntoBSet(int index, Node elem) {
    // If there are less # of bsets than the index,
    // create a new BSet and add the element
    if(partitions.size() <= index) {
      List<Node> newList = new ArrayList<Node>();
      newList.add(elem);
      partitions.add(new BSet(numBSets + 1, new Chain(++chainId, newList)));
      numBSets ++;
      // insert the element to the index-th bset
    } else {
      BSet bset = partitions.get(index);
      List<Chain> chains = bset.getAppendableChains(elem);

      if(chains.isEmpty()) {
        // create a new chain in the BSet
        if(bset.size() < index+1) {
          List<Node> newList = new ArrayList<Node>();
          newList.add(elem);
          bset.addChain(new Chain(++chainId, newList));
          // BSet is full, move to the next BSet
        } else {
          insertIntoBSet(index + 1, elem);
        }
        // The element fits in a single chain, append it
      } else if(chains.size() == 1) {
        Chain chainToAdd = (Chain)chains.toArray()[0];
        chainToAdd.append(elem);

      // The element fits in more than one chains, append to one of them and update B(i-1) and B(i)
      } else {

        List<Chain> leftBSetChains = partitions.get(index-1).getChains();
        List<Chain> currentBSetChains = partitions.get(index).getChains();

        Chain chainToAdd = (Chain)chains.toArray()[0];
        chainToAdd.append(elem);

        currentBSetChains.remove(chainToAdd);
        partitions.set(index-1, new BSet(index, currentBSetChains));

        leftBSetChains.add(chainToAdd);
        partitions.set(index, new BSet(index+1, leftBSetChains));

      }
    }
  }

  public Chain getChainByHeadEventId(String eventId) {
    List<Chain> chains = getChains();

    for(Chain c: chains) {
      if(c.getNodeToConsume() != null && c.getNodeToConsume().getId().equals(eventId)) return c;
    }

    return null;
  }

  public Chain getChainByHeadTransitionId(long transitionId) {
    List<Chain> chains = getChains();

    for(Chain c: chains) {
      if(c.getNodeToConsume() != null && c.getNodeToConsume().getTransition().getTransitionId() == transitionId) return c;
    }

    return null;
  }

  public boolean hasTransition(long transitionId) {
    return transitionIds.contains(transitionId);
  }

  public Chain getChainById(long chainId) {
    List<Chain> allChains = getChains();

    for(Chain c: allChains) {
      if(c.getId() == chainId) return c;
    }

    return null;
  }

  public boolean hasCurrentTransition(long transitionId) {
    List<Long> notExecuted = new ArrayList<Long>();

    List<Chain> allChains = getChains();
    for(Chain c: allChains) {
      for(Node n: c.getNodesToConsume())
        notExecuted.add(n.getTransition().getTransitionId());
    }

    return notExecuted.contains(transitionId);
  }

  public List<Chain> getChains() {
    List<Chain> allChains = new ArrayList<Chain>();
    for(BSet b: partitions) {
      Iterator<Chain> chainsInB = b.getChains().iterator();
      while(chainsInB.hasNext())
        allChains.add((Chain) chainsInB.next());
    }
    return allChains;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder("");
    for(BSet bset: partitions) {
      //sb.append("BSET of max size ").append(bset.getCapacity()).append(" :");
      sb.append(bset.toString());
      sb.append("\n");
    }

    return sb.toString();
  }
}
