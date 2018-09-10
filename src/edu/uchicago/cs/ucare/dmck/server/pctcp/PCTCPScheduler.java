package edu.uchicago.cs.ucare.dmck.server.pctcp;

import edu.uchicago.cs.ucare.dmck.transition.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PCTCPScheduler {
  protected static Logger logger = LoggerFactory.getLogger("PCTCPScheduler");

  private PCTCPOptions options;
  private Random random;

  private ChainPartitioner cp = new ChainPartitioner();

  private int numPrChangePts;
  private List<Integer> prChangePts; // assigned randomly
  private List<String> prChangeEventIds = new ArrayList<String>();

  // Contains chains in the order of priorities --> higher indexed chains have higher priorities
  // The first numPrChangePts chains are reserved for the reduced priority chains (initially no chain, i.e., id -1)
  private List<Long> priorityChains;

  private List<String> scheduledEvents;
  private List<Node> scheduledNodes;
  private List<Long> scheduledTxnIds;
  private List<Long> scheduledChainIds;

  private Set<String> priorityReducedAt;
  private int pathIndex = 0; // number of scheduled events

  private int maxNumAvailableChains = 0; // for stats
  //private TraceRecorder traceRecorder = new TraceRecorder("events" + + LocalDateTime.now().toString());

  public PCTCPScheduler(PCTCPOptions pctpOptions) {
    options = pctpOptions;
    random = new Random(options.getRandomSeed());
    // bugDepth #events to order, bugDepth - 1 #priority-change-points in PCTCPScheduler
    numPrChangePts = options.getBugDepth() - 1;

    logger.info("\n--------------- PCTCPScheduler running with seed: " + options.getRandomSeed());

    prChangePts = new ArrayList<Integer>();

    priorityChains = new ArrayList<Long>();
    for(int i = 0; i < numPrChangePts; i++)
      priorityChains.add(-1L);

    scheduledEvents = new ArrayList<String>();
    scheduledNodes = new ArrayList<Node>();
    scheduledTxnIds = new ArrayList<Long>();
    scheduledChainIds = new ArrayList<Long>();
    priorityReducedAt = new HashSet<String>();

    for(int i = 0; i < numPrChangePts; i++) {
       prChangePts.add(random.nextInt(options.getWindowEndIndex() - options.getWindowStartIndex()) + options.getWindowStartIndex());
    }

    logger.info("Priority change points: " + prChangePts);

    for(int index: prChangePts) {
       prChangeEventIds.add(eventLabels.get(index));
    }
  }

  public void addNewEvents(List<Transition> transitions) {
    sortTransitions(transitions);

    for(Transition t: transitions) {
      if(!cp.hasCurrentTransition(t.getTransitionId())) {
        cp.insert(t);
      }
    }

    Set<Long> newChains = new HashSet<Long>(); // returns all the current chains
    newChains.addAll(cp.getChainIds());
    Set<Long> prevChains = new HashSet<Long>(priorityChains);
    newChains.removeAll(prevChains);

    // assign a random priority for each newly added chain
    for (Long chain : newChains) {
      int pos = random.nextInt(priorityChains.size() - numPrChangePts + 1) + numPrChangePts - 1;
      priorityChains.add(pos, chain);
    }

    // insert the new chains as the least priority chains
    List<Long> chains = new ArrayList<Long>();
    chains.addAll(newChains);

    // update maxNumAvailableChains for stats
    int numAvailableChains = getAvailableChainSize();
    if(maxNumAvailableChains < numAvailableChains)
      maxNumAvailableChains = numAvailableChains;
  }

  public Transition scheduleNext() {
    Chain currentChain = getCurrentChain();
    if (currentChain == null) {
      logger.error("Current chain is null. \n" + toString());
      System.exit(-1);
      return null;
    }

    return doSchedule(currentChain); // do post processing
  }

  /**
   * Reduce priority of the given chain, based on its index value
   * @param currentChain - the chain whose priority will be reduced
   * @param index - index of the current priority change point in the tuple of priority changes
   */
  private void reducePriority(Chain currentChain, int index) {
    int prevIndex = priorityChains.indexOf(currentChain.getId());

    if(prevIndex >= numPrChangePts) { // high priority chain is getting reduced
      priorityChains.remove(currentChain.getId());
    } else { // reduced priority chain is getting reduced
      priorityChains.set(prevIndex, -1L);
    }

    priorityChains.set(numPrChangePts - index - 1, currentChain.getId()); // preserve order!

    //logger.debug("Chains ordered by priority after: ");
    printPriorities();
  }

  /**
   * Finds the chain to be scheduled next
   * @return chain id of the current chain, -1 id none //todo -1 case
   */
  private Chain getCurrentChain() {
    for(int i = priorityChains.size()-1; i >= 0; i--) {
      long chainId = priorityChains.get(i);
      if(chainId > 0) {
        Chain c = cp.getChainById(chainId);
        if(c.hasNodeToConsume() && okToSchedule(c.getNodeToConsume())) return checkForPriorityChange(c);
      }
    }

    return null;
  }

  private Chain checkForPriorityChange(Chain currentChain) {
    Node node = currentChain.getNodeToConsume();

    if(prChangeEventIds.contains(node.getId()) && !priorityReducedAt.contains(node.getId())) {
      int index = prChangeEventIds.indexOf(node.getId());
      reducePriority(currentChain, index);
      priorityReducedAt.add(node.getId());
      logger.debug("----------------------Reducing priority at msg: " + node.getId() + " MSG " + node.getTransition());
      currentChain = getCurrentChain();
      assert(currentChain != null);
    }

    return currentChain;
  }


  private Transition doSchedule(Chain chain) {
    Node nodeToSchedule = chain.consume();
    if(nodeToSchedule == null) {
      logger.error("Null node to schedule\n" + toString());
      System.exit(-1);
    }
    scheduledEvents.add(nodeToSchedule.getId());
    scheduledChainIds.add(chain.getId());
    scheduledNodes.add(nodeToSchedule);
    scheduledTxnIds.add(nodeToSchedule.getTransition().getTransitionId());
    logger.debug("Scheduling chain: " + chain.getId());
    //logger.debug("ALL: \n" + chainsToString());

    pathIndex ++;
    return nodeToSchedule.getTransition();
  }

  // might be overwritten for some systems
  private boolean okToSchedule(Node eventNode) {
    return true;
  }

  // for stats
  private int getAvailableChainSize() {
    int count = 0;
    for(Chain c: cp.getChains()) {
      if(c.hasNodeToConsume()) count ++;
    }
    return count;
  }

  public List<String> getSchedule() {
    return new ArrayList<String>(scheduledEvents);
  }

  public List<Long> getScheduleByChains() {
    return new ArrayList<Long>(scheduledChainIds);
  }

  public List<Node> getScheduleByNodes() {
    return new ArrayList<Node>(scheduledNodes);
  }

  public List<Long> getScheduleByTxnIds() {
    return new ArrayList<Long>(scheduledTxnIds);
  }

  public int getMaxNumAvailableChains() {
    return maxNumAvailableChains;
  }

  // prints the schedule by events..
  public void printSchedule() {
    StringBuilder sb = new StringBuilder("Schedule: \n");
    for(String s: scheduledEvents) {
      sb.append(s).append("\n");
    }
    logger.info(sb.toString());
    logger.info(cp.toString());

    logger.info("Priority changed at: " + prChangeEventIds);
  }

  public String chainsToString() {
    StringBuilder sb = new StringBuilder("[Chains: \n");
    for(int i = priorityChains.size()-1; i >= numPrChangePts; i--) {
      sb.append(cp.getChainById(priorityChains.get(i))).append("\n");
    }

    sb.append(" + \n");

    for(int i = numPrChangePts - 1; i >= 0; i--) {
      if(priorityChains.get(i) > 0)
        sb.append(cp.getChainById(priorityChains.get(i))).append("\n");
    }
    return sb.append("]").toString();
  }

  // State of the scheduler
  public String toString() {
    StringBuilder sb = new StringBuilder("\nPCTCPScheduler using: ");
    sb.append("Seed: ").append(options.getRandomSeed()).append("  ");
    sb.append("\nPriority change points: ").append(prChangePts).append("\n");
    return sb.toString();
  }

  public void printPriorities() {
    StringBuilder sb = new StringBuilder("Priorities with increasing indices (priorities): \n");
    sb.append("HIGH: (from lowest to highest order)");
    for(int i = priorityChains.size()-1; i >= numPrChangePts; i--) {
      sb.append(priorityChains.get(i)).append(" ");
    }

    sb.append(" + \nREDUCED: (from highest to lowest order)");

    for(int i = numPrChangePts - 1; i >= 0; i--) {
      if(priorityChains.get(i) > 0)
        sb.append(priorityChains.get(i)).append(" ");
    }
    logger.info(sb.toString());
  }



  public static void sortTransitions(List<Transition> transitions) {
    // sort transitions by event id
    Collections.sort(transitions, new Comparator<Transition>() {
      @Override
      public int compare(Transition o1, Transition o2) {
        String id1 = ChainPartitioner.getEventId(o1);
        String id2 = ChainPartitioner.getEventId(o2);

        if(id1.compareTo(id2) < 0) return -1;
        else if (id2.compareTo(id1) > 0) return 1;
        return 0;
      }
    });
  }

  // todo - move to a file
  private List<String> eventLabels = Arrays.asList(
      "Req-0--PAXOS_PREPARE--From-0--To-0",
      "Req-0--PAXOS_PREPARE--From-0--To-1",
      "Req-0--PAXOS_PREPARE--From-0--To-2",
      "Req-0--PAXOS_PREPARE_RESPONSE--From-0--To-0",
      "Req-0--PAXOS_PREPARE_RESPONSE--From-1--To-0",
      "Req-0--PAXOS_PREPARE_RESPONSE--From-2--To-0",
      "Req-0--PAXOS_PROPOSE--From-0--To-0",
      "Req-0--PAXOS_PROPOSE--From-0--To-1",
      "Req-0--PAXOS_PROPOSE--From-0--To-2",
      "Req-0--PAXOS_PROPOSE_RESPONSE--From-0--To-0",
      "Req-0--PAXOS_PROPOSE_RESPONSE--From-1--To-0",
      "Req-0--PAXOS_PROPOSE_RESPONSE--From-2--To-0",
      "Req-0--PAXOS_COMMIT--From-0--To-0",
      "Req-0--PAXOS_COMMIT--From-0--To-1",
      "Req-0--PAXOS_COMMIT--From-0--To-2",
      "Req-0--PAXOS_COMMIT_RESPONSE--From-0--To-0",
      "Req-0--PAXOS_COMMIT_RESPONSE--From-1--To-0",
      "Req-0--PAXOS_COMMIT_RESPONSE--From-2--To-0",

      "Req-1--PAXOS_PREPARE--From-1--To-0",
      "Req-1--PAXOS_PREPARE--From-1--To-1",
      "Req-1--PAXOS_PREPARE--From-1--To-2",
      "Req-1--PAXOS_PREPARE_RESPONSE--From-0--To-1",
      "Req-1--PAXOS_PREPARE_RESPONSE--From-1--To-1",
      "Req-1--PAXOS_PREPARE_RESPONSE--From-2--To-1",
      "Req-1--PAXOS_PROPOSE--From-1--To-0",
      "Req-1--PAXOS_PROPOSE--From-1--To-1",
      "Req-1--PAXOS_PROPOSE--From-1--To-2",
      "Req-1--PAXOS_PROPOSE_RESPONSE--From-0--To-1",
      "Req-1--PAXOS_PROPOSE_RESPONSE--From-1--To-1",
      "Req-1--PAXOS_PROPOSE_RESPONSE--From-2--To-1",
      "Req-1--PAXOS_COMMIT--From-1--To-0",  
      "Req-1--PAXOS_COMMIT--From-1--To-1",
      "Req-1--PAXOS_COMMIT--From-1--To-2",
      "Req-1--PAXOS_COMMIT_RESPONSE--From-0--To-1", 
      "Req-1--PAXOS_COMMIT_RESPONSE--From-1--To-1",
      "Req-1--PAXOS_COMMIT_RESPONSE--From-2--To-1",

      "Req-2--PAXOS_PREPARE--From-2--To-0",
      "Req-2--PAXOS_PREPARE--From-2--To-1",
      "Req-2--PAXOS_PREPARE--From-2--To-2",
      "Req-2--PAXOS_PREPARE_RESPONSE--From-0--To-2",
      "Req-2--PAXOS_PREPARE_RESPONSE--From-1--To-2",
      "Req-2--PAXOS_PREPARE_RESPONSE--From-2--To-2",
      "Req-2--PAXOS_PROPOSE--From-2--To-0",
      "Req-2--PAXOS_PROPOSE--From-2--To-1",
      "Req-2--PAXOS_PROPOSE--From-2--To-2",
      "Req-2--PAXOS_PROPOSE_RESPONSE--From-0--To-2",
      "Req-2--PAXOS_PROPOSE_RESPONSE--From-1--To-2",
      "Req-2--PAXOS_PROPOSE_RESPONSE--From-2--To-2",

      "Req-2--PAXOS_COMMIT--From-2--To-0",
      "Req-2--PAXOS_COMMIT--From-2--To-1",
      "Req-2--PAXOS_COMMIT--From-2--To-2",
      "Req-2--PAXOS_COMMIT_RESPONSE--From-0--To-2",
      "Req-2--PAXOS_COMMIT_RESPONSE--From-1--To-2",
      "Req-2--PAXOS_COMMIT_RESPONSE--From-2--To-2"
);
}
