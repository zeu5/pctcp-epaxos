package edu.uchicago.cs.ucare.dmck.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

import edu.uchicago.cs.ucare.dmck.protocol.FileRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.raft.RaftWorkloadDriver;
import edu.uchicago.cs.ucare.dmck.transition.AbstractNodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.AbstractNodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.AbstractNodeStartTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.dmck.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.dmck.transition.SleepTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;
import edu.uchicago.cs.ucare.dmck.util.LocalState;
import edu.uchicago.cs.ucare.example.election.LeaderElectionMain;

public abstract class ModelCheckingServerAbstract implements ModelCheckingServer {

  public static String DMCK_NAME;

  protected static Logger LOG = LoggerFactory.getLogger(ModelCheckingServerAbstract.class);

  private static String PATH_FILE = "path";
  private static String DEBUG_FILE = "debug.log";
  private static String PERF_FILE = "performance.log";
  private static String RESULT_FILE = "result";

  protected LinkedBlockingQueue<Event> packetQueue;
  protected boolean hasFinishedAllExploration;

  public int numNode;
  public int numCurrentCrash;
  public int numCurrentReboot;
  protected int[] localState;
  public boolean[] isNodeOnline;
  public int[][][][] vectorClocks; // pair of channel perspective of vector
  // clock for each pair of channel
  public String workingDirPath;

  protected ConcurrentLinkedQueue<Event>[][] messagesQueues;
  protected LinkedList<Event> localEventQueue;

  protected int testId;

  protected String testRecordDirPath;
  protected String allEventsDBDirPath;
  protected String idRecordDirPath;
  protected String pathRecordFilePath;
  protected String performanceRecordFilePath;
  protected String debugRecordFilePath;
  protected String resultFilePath;
  protected File allEventsDBDir;
  protected FileOutputStream pathRecordFile;
  protected FileOutputStream performanceRecordFile;
  protected FileOutputStream debugRecordFile;
  protected FileOutputStream resultFile;

  protected WorkloadDriver workloadDriver;
  protected SpecVerifier verifier;

  public LinkedList<Transition> currentEnabledTransitions = new LinkedList<Transition>();
  protected boolean[] isNodeSteady;
  protected Boolean isStarted;
  protected Thread modelChecking;
  protected int[] numPacketSentToId;

  protected LinkedList<String> directedInitialPath = new LinkedList<String>();
  protected int directedInitialPathCounter;
  protected boolean hasDirectedInitialPath;
  protected boolean hasFinishedDirectedInitialPath;

  // dmck config
  protected int steadyStateTimeout;
  protected int initSteadyStateTimeout;
  protected int waitEndExploration;
  protected int workloadInjectionWaitingTime;
  protected boolean tcpParadigm;

  // dmck vars for Cass
  public HashMap<Integer, String> workloadHasApplied;

  // dmck vars for Raft
  protected int leaderElectionTimeout;
  protected int snapshotWaitingTime;
  public int timeoutEventIterations;
  public int[] timeoutEventCounter;
  public boolean[] initTimeoutEnabling;
  protected boolean waitForNextLE;
  protected int waitedForNextLEInDiffTermCounter;

  public LocalState[] localStates;
  protected String ipcDir;

  // workload
  public boolean hasInitWorkload;
  public int numInitWorkload;
  public int numQueueInitWorkload;
  public boolean hasMidWorkload;
  public int numMidWorkload;
  public int numQueueMidWorkload;

  // file watcher
  private FileWatcher fileWatcher;
  private Thread watcherThread;

  // reproducedBug
  protected String expectedResultFilePath;

  // performance evaluation
  public int currentStep;
  public Timestamp lastTimeEnabledEvent;
  public Timestamp lastTimeNewEventOrStateUpdate;
  public Timestamp startTimeTSInit;
  public Timestamp endTimeTSInit;
  public Timestamp startTimePathExecution;
  public Timestamp endTimePathExecution;
  public Timestamp startTimeVerification;
  public Timestamp endTimeVerification;
  public Timestamp startTimeEvaluation;
  public Timestamp endTimeEvaluation;

  // quick event release
  public boolean useSequencer;
  public boolean quickEventReleaseMode;
  public boolean isQuickEventStep;
  public int[] senderSequencer;
  public int[] receiverSequencer;
  public LocalState[] perEventStateBatch;
  public String[] stateUpdateBook;


  @SuppressWarnings("unchecked")
  public ModelCheckingServerAbstract(String dmckName, FileWatcher fileWatcher, int numNode,
      String testRecordDirPath, String workingDirPath, WorkloadDriver workloadDriver,
      String ipcDir) {
    LOG = LoggerFactory.getLogger(ModelCheckingServerAbstract.class + "." + dmckName);
    DMCK_NAME = dmckName;
    packetQueue = new LinkedBlockingQueue<Event>();
    hasFinishedAllExploration = false;
    this.numNode = numNode;
    this.testRecordDirPath = testRecordDirPath;
    this.workingDirPath = workingDirPath;
    this.allEventsDBDirPath = this.workingDirPath + "/" + "all-events-db";
    this.allEventsDBDir = new File(this.allEventsDBDirPath);
    this.workloadDriver = workloadDriver;
    this.verifier = workloadDriver.verifier;
    pathRecordFile = null;
    performanceRecordFile = null;
    debugRecordFile = null;
    resultFile = null;
    isNodeOnline = new boolean[numNode];
    // +1 for crash or reboot injection
    vectorClocks = new int[numNode][numNode + 1][numNode][numNode + 1];
    messagesQueues = new ConcurrentLinkedQueue[numNode][numNode];
    localEventQueue = new LinkedList<Event>();
    localStates = new LocalState[numNode];
    hasInitWorkload = false;
    hasMidWorkload = false;
    this.ipcDir = ipcDir;
    lastTimeEnabledEvent = new Timestamp(System.currentTimeMillis());
    lastTimeNewEventOrStateUpdate = new Timestamp(System.currentTimeMillis());
    this.fileWatcher = fileWatcher;
    watcherThread = new Thread(this.fileWatcher);
    getDMCKConfig();

    watcherThread.start();

    // Added for collecting stats for PCTCP and Random exploration //B
    startTime = System.currentTimeMillis();  //B
    recorder = new FileRecorder("Test" + LocalDateTime.now().toString() + ".txt"); //B

    resetTest();
  }

  public void getDMCKConfig() {
    try {
      String dmckConfigFile = workingDirPath + "/dmck.conf";
      Properties dmckConf = new Properties();
      FileInputStream configInputStream = new FileInputStream(dmckConfigFile);
      dmckConf.load(configInputStream);
      configInputStream.close();

      // mandatory config
      initSteadyStateTimeout = Integer.parseInt(dmckConf.getProperty("init_steady_state_timeout"));
      steadyStateTimeout = Integer.parseInt(dmckConf.getProperty("steady_state_timeout"));
      waitEndExploration = Integer.parseInt(dmckConf.getProperty("wait_end_exploration"));

      // optional config
      workloadInjectionWaitingTime =
          Integer.parseInt(dmckConf.getProperty("wait_before_workload_injection", "0"));
      useSequencer = dmckConf.getProperty("use_sequencer", "false").equals("true");
      quickEventReleaseMode = dmckConf.getProperty("quick_event_release", "false").equals("true");
      tcpParadigm = dmckConf.getProperty("tcp_paradigm", "true").equals("true");
      if (DMCK_NAME.equals("raftModelChecker")) {
        leaderElectionTimeout = Integer.parseInt(dmckConf.getProperty("leader_election_timeout"));
        timeoutEventIterations = Integer.parseInt(dmckConf.getProperty("timeout_event_iterations"));
        snapshotWaitingTime = Integer.parseInt(dmckConf.getProperty("snapshot_waiting_time"));
      }

      // sanity check
      if (quickEventReleaseMode && !useSequencer) {
        LOG.error("DMCK expects use_sequencer=true, if quick_event_release=true.");
        System.exit(1);
      }

    } catch (Exception e) {
      LOG.error("Error in reading dmck config file");
    }
  }

  public void setInitWorkload(int numWorkload) {
    this.numInitWorkload = numWorkload;
    this.numQueueInitWorkload = numWorkload;
    if (numWorkload > 0)
      this.hasInitWorkload = true;
  }

  public void setMidWorkload(int numWorkload) {
    this.numMidWorkload = numWorkload;
    this.numQueueMidWorkload = numWorkload;
    if (numWorkload > 0) {
      this.hasMidWorkload = true;
    }
  }

  public void setExpectedResultPath(String filePath) {
    if (!filePath.isEmpty()) {
      this.expectedResultFilePath = filePath;
    }
  }

  public void setDirectedInitialPath(String directedInitialPath) {
    this.hasDirectedInitialPath = !directedInitialPath.isEmpty();
    this.hasFinishedDirectedInitialPath = !hasDirectedInitialPath;
    if (hasDirectedInitialPath) {
      LOG.info("InitialPath: " + directedInitialPath);
      readInitialPath(directedInitialPath);
    }
  }

  // read file from initialPath file
  public void readInitialPath(String initialPath) {
    try {
      BufferedReader initialPathReader = new BufferedReader(new FileReader(initialPath));
      String line;
      while ((line = initialPathReader.readLine()) != null) {
        this.directedInitialPath.add(line);
      }
      initialPathReader.close();
    } catch (Exception e) {
      LOG.error("Error in readInitialPath");
    }
  }

  public void offerPacket(Event event) {
      messagesQueues[(int) event.getValue("sendNode")][(int) event.getValue("recvNode")].add(event);
  }

  public void offerLocalEvent(Event event) {
    localEventQueue.add(event);
  }

  abstract protected void adjustCrashAndReboot(LinkedList<Transition> transitions);

  public void executeMidWorkload() {
    if (hasMidWorkload) {
      if (DMCK_NAME.equals("raftModelChecker")) {
        executeRaftSnapshot();
      }
    }
  }

  public void updateSAMCQueueWithoutLog() {
    getOutstandingEventTransition(currentEnabledTransitions);
    adjustCrashAndReboot(currentEnabledTransitions);
  }

  public void updateSAMCQueue(LocalState[] currentGS) {
    updateSAMCQueueWithoutLog();
    collectDebugData(currentGS);
  }

  public void updateSAMCQueueAfterEventExecution(Transition transition) {
    if (transition instanceof NodeCrashTransition) {
      NodeCrashTransition crash = (NodeCrashTransition) transition;
      ListIterator<Transition> iter = currentEnabledTransitions.listIterator();
      while (iter.hasNext()) {
        Transition t = iter.next();
        if (t instanceof PacketSendTransition) {
          PacketSendTransition p = (PacketSendTransition) t;
          if (p.getPacket().getFromId() == crash.getId()
              || p.getPacket().getToId() == crash.getId()) {
            p.getPacket().setObsolete(true);
            p.getPacket().setObsoleteBy(crash.getId());
          }
        }
      }
      for (ConcurrentLinkedQueue<Event> queue : messagesQueues[crash.getId()]) {
        queue.clear();
      }
    }
  }

  public void getOutstandingEventTransition(LinkedList<Transition> transitionList) {
    boolean[][] filter = new boolean[numNode][numNode];
    if (tcpParadigm) {
      for (int i = 0; i < numNode; ++i) {
        Arrays.fill(filter[i], true);
      }
      for (Transition t : transitionList) {
        if (t instanceof PacketSendTransition) {
          PacketSendTransition p = (PacketSendTransition) t;
          filter[p.getPacket().getFromId()][p.getPacket().getToId()] = false;
        }
      }
    }
    LinkedList<PacketSendTransition> buffer = new LinkedList<PacketSendTransition>();
    for (int i = 0; i < numNode; ++i) {
      for (int j = 0; j < numNode; ++j) {
        if (tcpParadigm) {
          // for TCP connection paradigm
          if (filter[i][j] && !messagesQueues[i][j].isEmpty()) {
            buffer.add(new PacketSendTransition(this, messagesQueues[i][j].remove()));
          }
        } else {
          // for socket / UDP connection paradigm
          if (!messagesQueues[i][j].isEmpty()) {
            buffer.add(new PacketSendTransition(this, messagesQueues[i][j].remove()));
          }
        }
      }
    }
    Collections.sort(buffer, new Comparator<PacketSendTransition>() {
      public int compare(PacketSendTransition o1, PacketSendTransition o2) {
        Long i1 = o1.getPacket().getId();
        Long i2 = o2.getPacket().getId();
        return i1.compareTo(i2);
      }
    });
    transitionList.addAll(buffer);

    // add local events to queue
    getLocalEvents(transitionList);
  }

  public void executeRaftSnapshot() {
    if (numQueueMidWorkload > 0) {
      boolean leaderExist = false;
      boolean noCandidate = true;
      int leaderId = -1;
      for (int i = 0; i < numNode; i++) {
        if ((int) localStates[i].getValue("state") == 2) {
          if (!leaderExist) {
            // one leader exists
            leaderExist = true;
            leaderId = i;
          } else {
            // more than one leader exist
            leaderExist = false;
            break;
          }
        } else if ((int) localStates[i].getValue("state") == 1) {
          noCandidate = false;
        }
      }
      if (leaderExist && noCandidate) {
        collectDebugWorkload("snapshot at leader in node-" + leaderId);
        raftSnapshot(leaderId);
        numQueueMidWorkload--;
      }
    }
  }

  public void getLocalEvents(LinkedList<Transition> transitionList) {
    LinkedList<PacketSendTransition> buffer = new LinkedList<PacketSendTransition>();
    for (int i = localEventQueue.size() - 1; i > -1; i--) {
      buffer.add(new PacketSendTransition(this, localEventQueue.remove(i)));
    }
    transitionList.addAll(buffer);
  }

  public void setTestId(int testId) {
    LOG.debug("Path ID=" + testId);
    this.testId = testId;
    idRecordDirPath = testRecordDirPath + "/" + testId;
    File testRecordDir = new File(idRecordDirPath);
    if (!testRecordDir.exists()) {
      testRecordDir.mkdir();
    }
    pathRecordFilePath = idRecordDirPath + "/" + PATH_FILE;
    debugRecordFilePath = idRecordDirPath + "/" + DEBUG_FILE;
    performanceRecordFilePath = idRecordDirPath + "/" + PERF_FILE;
    resultFilePath = idRecordDirPath + "/" + RESULT_FILE;
  }

  public boolean addLocalStatesUpdate(int id, LocalState newState) {
    String s = newState.toString();
    if (quickEventReleaseMode) {
      if (stateUpdateBook[id].contains(s)) {
        return false;
      }
      stateUpdateBook[id] += s;
      LOG.debug("currentStateUpdateBook at node-" + id + "=" + stateUpdateBook[id]);
      LOG.debug("new state=" + s);
    }
    return true;
  }

  public void addOrIgnoreLocalStatesUpdate(int id, LocalState newState) {
    if (addLocalStatesUpdate(id, newState)) {
      for (String key : newState.getAllKeys()) {
        localStates[id].setKeyValue(key, newState.getValue(key));
      }
    } else {
      LOG.debug("Ignore Local State Update at node-" + id + "=" + newState.toString());
    }
  }

  public void addOrIgnorePerBatchUpdates() {
    for (int id = 0; id < numNode; id++) {
      if (perEventStateBatch[id].getAllKeys().length > 0) {
        addOrIgnoreLocalStatesUpdate(id, perEventStateBatch[id]);
      }
      perEventStateBatch[id] = new LocalState();
    }
  }

  public void addStateToEventBatch(int id, LocalState newState) {
    if (quickEventReleaseMode) {
      for (String key : newState.getAllKeys()) {
        perEventStateBatch[id].setKeyValue(key, newState.getValue(key));
      }
    } else {
      addOrIgnoreLocalStatesUpdate(id, newState);
    }
  }

  public void updateLocalState(int id, int state) {
    localState[id] = state;
    LOG.debug("Node " + id + " update its local state to be " + state);
  }

  public void recordEventToPathFile(String event) {
    try {
      pathRecordFile.write((event + "\n").getBytes());
    } catch (IOException e) {
      LOG.error("", e);
    }
  }

  public void saveResult(boolean verifiedResult, String desc) {
    String result = verifiedResult + " ; " + desc + "\n";
    try {
      if (resultFile == null) {
        resultFile = new FileOutputStream(resultFilePath);
      }
      resultFile.write(result.getBytes());
    } catch (IOException e) {
      LOG.error("", e);
    }
  }

  protected void initGlobalState() {
    try {
      pathRecordFile = new FileOutputStream(pathRecordFilePath);
      performanceRecordFile = new FileOutputStream(performanceRecordFilePath);
      debugRecordFile = new FileOutputStream(debugRecordFilePath);
    } catch (FileNotFoundException e) {
      LOG.error("", e);
    }
  }

  public boolean runNode(int id, int[][] vectorClock) {
    if (isNodeOnline(id)) {
      return true;
    }
    workloadDriver.startNode(id);
    setNodeOnline(id, true);
    setNodeSteady(id, false);
    updateVectorClockForCrashOrReboot(id, vectorClock);
    try {
      int timeoutCounter = 0;
      int timeoutFraction = 20;
      while (!isNodeSteady(id) && timeoutCounter <= timeoutFraction) {
        Thread.sleep(initSteadyStateTimeout / timeoutFraction);
        timeoutCounter++;
      }

      if (timeoutCounter >= timeoutFraction) {
        LOG.debug("Steady state for new started node " + id + " triggered by timeout.");
      }

      setNodeSteady(id, true);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return true;
  }

  public boolean killNode(int id, int[][] vectorClock) {
    workloadDriver.stopNode(id);
    setNodeOnline(id, false);
    initTimeoutEnabling[id] = false;
    for (int i = 0; i < numNode; ++i) {
      messagesQueues[i][id].clear();
      messagesQueues[id][i].clear();
      if (i != id) {
        setNodeSteady(i, false);
      }
    }
    updateVectorClockForCrashOrReboot(id, vectorClock);
    localStates[id] = resetNodeState(id);
    waitOnSteadyStatesByTimeout(steadyStateTimeout);

    return true;
  }

  public boolean startEnsemble() {
    // Performance metrics
    startTimeTSInit = new Timestamp(System.currentTimeMillis());

    for (int i = 0; i < numNode; ++i) {
      setNodeOnline(i, true);
    }
    fileWatcher.setAcceptFile(true);
    workloadDriver.startEnsemble();
    if (hasInitWorkload) {
      if (workloadInjectionWaitingTime > 0) {
        try {
          LOG.info("Additional waiting time = " + workloadInjectionWaitingTime
              + "ms, for all nodes to be steady, before injecting Workload");
          Thread.sleep(workloadInjectionWaitingTime);
        } catch (InterruptedException e) {
          LOG.error("Error in waiting for workload injection.");
        }
      }
      workloadDriver.startWorkload();
    }
    return true;
  }

  public boolean stopEnsemble() {
    if (fileWatcher.acceptFile) {
      workloadDriver.stopEnsemble();
      if (hasInitWorkload) {
        workloadDriver.stopWorkload();
      }
      fileWatcher.setAcceptFile(false);
      for (int i = 0; i < numNode; ++i) {
        setNodeOnline(i, false);
        for (int j = 0; j < numNode; ++j) {
          messagesQueues[i][j].clear();
          messagesQueues[j][i].clear();
        }
      }
    }
    return true;
  }

  public void setNodeOnline(int id, boolean isOnline) {
    isNodeOnline[id] = isOnline;
  }

  public boolean isNodeOnline(int id) {
    return isNodeOnline[id];
  }

  public int[][] getVectorClock(int sender, int receiver) {
    return vectorClocks[sender][receiver];
  }

  public boolean hasReproducedBug() {
    if (expectedResultFilePath != null) {
      try {
        Path expectedPath = Paths.get(expectedResultFilePath);
        Path resultPath = Paths.get(resultFilePath);
        byte[] expectedFileByte = Files.readAllBytes(expectedPath);
        byte[] resultFileByte = Files.readAllBytes(resultPath);
        return Arrays.equals(expectedFileByte, resultFileByte);
      } catch (IOException e) {
        LOG.error("Error in comparing expected result file with result file:" + e.getMessage());
      }
    }
    return false;
  }

  public void collectDebugData(LocalState[] curGlobalState) {
    String content = "Update from Target System:\n";
    content += fileWatcher.getReceivedUpdates();

    content += "Global States:\n";
    for (int n = 0; n < numNode; n++) {
      content += "n-" + n + ": " + curGlobalState[n].toString() + "\n";
    }

    content += "Events in Queue:\n";
    int counter = 1;
    for (Transition t : currentEnabledTransitions) {
      if (t != null) {
        content += counter + ". " + t.toString() + "\n";
      } else {
        content += counter + ". null event\n";
      }
      counter++;
    }
    content += "------------------\n";
    //LOG.info(content);
    try {
      debugRecordFile.write(content.getBytes());
    } catch (IOException e) {
      LOG.error("", e);
    }
  }

  public void collectDebugNextTransition(Transition transition) {
    String content = "Next Event: " + transition.toString();
    if (isQuickEventStep) {
      content += " --> IS QUICK EVENT STEP";
    }
    content += "\n------------------\n";
    LOG.info(content);
    try {
      debugRecordFile.write(content.getBytes());
    } catch (IOException e) {
      LOG.error("", e);
    }
  }

  public void collectDebugWorkload(String event) {
    String content = "Execute Workload: " + event + "\n";
    content += "------------------\n";
    try {
      debugRecordFile.write(content.getBytes());
    } catch (IOException e) {
      LOG.error("", e);
    }
  }

  public void collectDebug(String content) {
    content += "------------------\n";
    try {
      debugRecordFile.write(content.getBytes());
    } catch (IOException e) {
      LOG.error("", e);
    }
  }

  public void collectPerformancePerEventMetrics() {
    if (currentStep > 0) {
      // Performance evaluation: Collect Round-trip time for DMCK in enabling an event
      // and receiving next event(s) or/and node state update(s)
      long maxRoundTripTime =
          lastTimeNewEventOrStateUpdate.getTime() - lastTimeEnabledEvent.getTime();

      // If there is no new event or state that came after an event is enabled, then
      // maxRoundTripTime will be < 0 milliseconds. At this condition, we can assume
      // that the maxRoundTripTime is 0.
      if (maxRoundTripTime < 0) {
        maxRoundTripTime = 0;
      }

      String content = currentStep + " : max-roundtrip-time=" + maxRoundTripTime + "ms;\n";
      try {
        performanceRecordFile.write(content.getBytes());
      } catch (Exception e) {
        LOG.error("", e);
      }
    }
    currentStep++;
  }

  public void collectPerformancePerPathMetrics() {
    // Performance evaluation: Collect time spent to execute a single path
    long totalInitializationTime = endTimeTSInit.getTime() - startTimeTSInit.getTime();
    long totalPathExecutionTime = endTimePathExecution.getTime() - startTimePathExecution.getTime();
    long totalVerificationTime = endTimeVerification.getTime() - startTimeVerification.getTime();
    long totalEvaluationTime = endTimeEvaluation.getTime() - startTimeEvaluation.getTime();

    String content = "-------\n";
    content += "SUMMARY\n";
    content += "-------\n";
    content += "total-initialization-time=" + totalInitializationTime + "ms;\n";
    content += "total-execution-path-time=" + totalPathExecutionTime + "ms;\n";
    content += "total-verification-time=" + totalVerificationTime + "ms;\n";
    content += "total-evaluation-time=" + totalEvaluationTime + "ms;\n";
    content += "sum-up-single-path-execution-time=" + (totalInitializationTime
        + totalPathExecutionTime + totalVerificationTime + totalEvaluationTime) + "ms;\n";
    try {
      performanceRecordFile.write(content.getBytes());
    } catch (Exception e) {
      LOG.error("", e);
    }
  }

  public void updateVectorClock(Event packet) {
    int fromId = (int) packet.getValue(Event.FROM_ID);
    int toId = (int) packet.getValue(Event.TO_ID);
    // increase the channel vector clock on its perspective
    vectorClocks[fromId][toId][fromId][toId]++;
    int[][] packetVectorClock = packet.getVectorClock();
    for (int i = 0; i < numNode; ++i) {
      if (fromId != toId) {
        vectorClocks[toId][i][fromId][toId]++;
      }
      for (int j = 0; j < numNode; ++j) {
        for (int k = 0; k < numNode + 1; ++k) {
          // sync receiver vector clocks against the packet
          if (packetVectorClock[j][k] > vectorClocks[toId][i][j][k]) {
            vectorClocks[toId][i][j][k] = packetVectorClock[j][k];
          }
        }
      }
    }
  }

  public void updateVectorClockForCrashOrReboot(int id, int[][] vectorClock) {
    vectorClocks[id][numNode][id][id]++;
    vectorClocks[id][numNode][id][numNode]++;
    for (int i = 0; i < numNode + 1; ++i) {
      for (int j = 0; j < numNode; ++j) {
        for (int k = 0; k < numNode + 1; ++k) {
          if (vectorClock[j][k] > vectorClocks[id][i][j][k]) {
            vectorClocks[id][i][j][k] = vectorClock[j][k];
          }
        }
      }
    }
  }

  protected void removeEventFromQueue(LinkedList<Transition> dmckQueue, Transition event) {
    boolean existInQueue = false;
    for (int index = 0; index < dmckQueue.size(); index++) {
      if (event instanceof PacketSendTransition
          && dmckQueue.get(index) instanceof PacketSendTransition
          && event.getTransitionId() == dmckQueue.get(index).getTransitionId()) {
        existInQueue = true;
      } else if (event instanceof NodeCrashTransition) {
        if (dmckQueue.get(index) instanceof AbstractNodeCrashTransition) {
          existInQueue = true;
        } else if (dmckQueue.get(index) instanceof NodeCrashTransition
            && dmckQueue.get(index).getId() == event.getId()) {
          existInQueue = true;
        }
      } else if (event instanceof NodeStartTransition) {
        if (dmckQueue.get(index) instanceof AbstractNodeStartTransition) {
          existInQueue = true;
        } else if (dmckQueue.get(index) instanceof NodeStartTransition
            && dmckQueue.get(index).getId() == event.getId()) {
          existInQueue = true;
        }
      }
      if (existInQueue) {
        dmckQueue.remove(index);
        break;
      }
    }
    if (!existInQueue) {
      LOG.error("Event=" + event.toString() + " does not exist in DMCK Queue!");
    }
  }

  public boolean commit(PacketSendTransition event) {
    // if the destination node / origin node is crashed, and the event is
    // still not obsolete,
    // set it to obsolete since it is not valid anymore
    boolean result;
    if (!event.getPacket().isObsolete()) {
      try {
        // Enable event.
        fileWatcher.enableEvent(event);

        // Performance evaluation
        collectPerformancePerEventMetrics();
        lastTimeEnabledEvent = new Timestamp(System.currentTimeMillis());

        updateVectorClock(event.getPacket());

        result = true;
      } catch (Exception e) {
        LOG.error("Error when committing event=" + event.toString());
        result = false;
      }
      if (result) {
        synchronized (numPacketSentToId) {
          numPacketSentToId[event.getPacket().getToId()]++;
        }
        return true;
      }
    } else {
      if (event.getPacket().getToId() == event.getPacket().getObsoleteBy()) {
        // Enable an event, but DMCK does not record it
        String filename = String.valueOf(event.getPacket().getValue(Event.FILENAME));
        try {
          PrintWriter writer = new PrintWriter(ipcDir + "/new/" + filename, "UTF-8");
          writer.println("eventId=" + event.getPacket().getId());
          writer.println("execute=false");
          writer.close();

          LOG.info("Enable obsolete event with ID : " + event.getTransitionId());

          Runtime.getRuntime()
              .exec("mv " + ipcDir + "/new/" + filename + " " + ipcDir + "/ack/" + filename);

          // Performance evaluation
          collectPerformancePerEventMetrics();
          lastTimeEnabledEvent = new Timestamp(System.currentTimeMillis());
        } catch (Exception e) {
          LOG.error("Error in creating commit file : " + filename);
        }
      }

      return true;
    }
    return false;
  }

  protected boolean isSystemSteady() {
    for (int i = 0; i < numNode; ++i) {
      if (!isNodeSteady(i)) {
        return false;
      }
    }
    return true;
  }

  public void informSteadyState(int id, int runningState) {
    setNodeSteady(id, true);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Node " + id + " is in steady state");
    }
    synchronized (isStarted) {
      if (!isStarted && isSystemSteady()) {
        isStarted = true;
        initGlobalState();
        LOG.debug("First system steady state, start dmck thread.");

        // Performance metrics to calculate path execution time.
        startTimePathExecution = new Timestamp(System.currentTimeMillis());
        modelChecking.start();
      }
    }
  }

  public void waitOnFirstSteadyStates() {
    // Performance metrics to calculate initialization time.
    endTimeTSInit = new Timestamp(System.currentTimeMillis());

    waitOnSteadyStatesByTimeout(initSteadyStateTimeout);
  }

  public void waitOnSteadyStatesByTimeout(int timeout) {
    LOG.debug("Wait on global steady states for " + timeout + "ms");
    try {
      Thread.sleep(timeout);
      for (int i = 0; i < numNode; i++) {
        informSteadyState(i, 0);
      }
    } catch (Exception e) {
      LOG.error("Error while waiting on the first steady states timeout");
    }
  }

  public void informActiveState(int id) {
    setNodeSteady(id, false);
  }

  protected void setNodeSteady(int id, boolean isSteady) {
    isNodeSteady[id] = isSteady;

  }

  protected boolean isNodeSteady(long id) {
    return isNodeSteady[(int) id] || !isNodeOnline[(int) id];
  }

  protected void waitNodeSteady(int id) throws InterruptedException {
    int timeoutCounter = 0;
    int timeoutFraction = 20;
    while (!isNodeSteady(id) && timeoutCounter <= timeoutFraction) {
      Thread.sleep(steadyStateTimeout / timeoutFraction);
      timeoutCounter++;
    }
    setNodeSteady(id, true);
  }

  public boolean commitAndWait(PacketSendTransition event) throws InterruptedException {
    int recvNode = event.getPacket().getToId();
    setNodeSteady(recvNode, false);
    if (commit(event)) {
      if (isQuickEventStep) {
        waitForCausalNewEvents(event);
      } else {
        waitNodeSteady(recvNode);
      }
      return true;
    } else {
      setNodeSteady(recvNode, true);
      return false;
    }
  }

  public void waitForCausalNewEvents(PacketSendTransition event) throws InterruptedException {
    // This function will only be accommodated in ReductionAlgorithmModelChecker
    waitNodeSteady(event.getPacket().getToId());
  }

  @SuppressWarnings("unchecked")
  public void resetTest() {
    LOG.debug("Test reset");
    fileWatcher.resetExecutionPathStats();
    messagesQueues = new ConcurrentLinkedQueue[numNode][numNode];
    currentStep = 0;
    numCurrentCrash = 0;
    numCurrentReboot = 0;
    directedInitialPathCounter = 0;
    hasFinishedDirectedInitialPath = !hasDirectedInitialPath;
    numQueueMidWorkload = numMidWorkload;
    numQueueInitWorkload = numInitWorkload;
    localState = new int[numNode];
    senderSequencer = new int[numNode];
    receiverSequencer = new int[numNode];
    stateUpdateBook = new String[numNode];
    perEventStateBatch = new LocalState[numNode];
    timeoutEventCounter = new int[numNode];
    initTimeoutEnabling = new boolean[numNode];
    for (int i = 0; i < numNode; i++) {
      timeoutEventCounter[i] = 0;
      initTimeoutEnabling[i] = false;
      senderSequencer[i] = 0;
      receiverSequencer[i] = 0;
      stateUpdateBook[i] = "";
      perEventStateBatch[i] = new LocalState();
    }
    isQuickEventStep = false;
    waitForNextLE = false;
    waitedForNextLEInDiffTermCounter = 0;
    if (pathRecordFile != null) {
      try {
        pathRecordFile.close();
      } catch (IOException e) {
        LOG.error("", e);
      }
    }
    if (performanceRecordFile != null) {
      try {
        performanceRecordFile.close();
      } catch (IOException e) {
        LOG.error("", e);
      }
    }
    if (debugRecordFile != null) {
      try {
        debugRecordFile.close();
      } catch (IOException e) {
        LOG.error("", e);
      }
    }
    if (resultFile != null) {
      try {
        resultFile.close();
        resultFile = null;
      } catch (IOException e) {
        LOG.error("", e);
      }
    }
    Arrays.fill(isNodeOnline, true);
    synchronized (this) {
      this.notifyAll();
    }
    for (int i = 0; i < numNode; ++i) {
      vectorClocks[i][i][i][i] = 1;
      vectorClocks[i][numNode][i][numNode] = 1; // for crash / reboot
      for (int j = 0; j < numNode + 1; ++j) {
        for (int k = 0; k < numNode; ++k) {
          for (int l = 0; l < numNode + 1; ++l) {
            vectorClocks[i][j][k][l] = 0;
          }
        }
        vectorClocks[i][j][i][j] = 1;
        if (j < numNode) {
          messagesQueues[i][j] = new ConcurrentLinkedQueue<Event>();
        }
      }
    }
    isNodeSteady = new boolean[numNode];
    isStarted = false;
    numPacketSentToId = new int[numNode];
    localStates = getInitialGlobalStates();

    // system specific
    if (DMCK_NAME.equals("cassChecker")) {
      this.workloadHasApplied = new HashMap<Integer, String>();
    }
  }

  public LocalState[] getInitialGlobalStates() {
    LocalState[] globalStates = new LocalState[numNode];
    for (int id = 0; id < numNode; ++id) {
      globalStates[id] = resetNodeState(id);
    }
    return globalStates;
  }

  public LocalState resetNodeState(int nodeId) {
    LocalState initialLS = new LocalState();
    if (DMCK_NAME.equals("scmChecker")) {
      initialLS.setKeyValue("vote", 0);
    } else if (DMCK_NAME.equals("sampleLEModelChecker")) {
      initialLS.setKeyValue("role", LeaderElectionMain.LOOKING);
      initialLS.setKeyValue("leader", -1);
    } else if (DMCK_NAME.equals("raftModelChecker")) {
      initialLS.setKeyValue("state", -1);
      initialLS.setKeyValue("term", -1);
    } else if (DMCK_NAME.startsWith("zkChecker")) {
      initialLS.setKeyValue("state", 0);
      initialLS.setKeyValue("proposedLeader", (long) nodeId);
      initialLS.setKeyValue("proposedZxid", (long) -1);
      initialLS.setKeyValue("logicalclock", (long) 1);
      HashMap<Long, String> votesHash = new HashMap<Long, String>();
      votesHash.put((long) nodeId, nodeId + ",-1");
      initialLS.setKeyValue("votesTable", votesHash);
    }
    return initialLS;
  }

  protected int waitForExpectedEvent(int retryCounter) {
    if (!isQuickEventStep) {
      retryCounter++;
      try {
        Thread.sleep(steadyStateTimeout / 2);
      } catch (Exception e) {
        LOG.error("", e);
      }
    }
    updateSAMCQueueWithoutLog();
    return retryCounter;
  }

  protected Transition transformInstructionToTransition(String[] instruction) {
    InstructionTransition i = null;
    if (instruction[0].equals("packetsend")) {
      String packetTransitionIdString = instruction[1].split("=")[1];
      if (packetTransitionIdString.equals("*")) {
        i = new PacketSendInstructionTransition(0);
      } else {
        long packetTransitionId = Long.parseLong(packetTransitionIdString);
        i = new PacketSendInstructionTransition(packetTransitionId);
      }
    } else if (instruction[0].equals("nodecrash")) {
      int id = Integer.parseInt(instruction[1].split("=")[1]);
      i = new NodeCrashInstructionTransition(id);
    } else if (instruction[0].equals("nodestart")) {
      int id = Integer.parseInt(instruction[1].split("=")[1]);
      i = new NodeStartInstructionTransition(id);
    } else if (instruction[0].equals("sleep")) {
      long sleep = Long.parseLong(instruction[1].split("=")[1]);
      i = new SleepInstructionTransition(sleep);
    } else if (instruction[0].equals("stop")) {
      i = new ExitInstructionTransaction();
    } else {
      LOG.error(
          "Instruction=" + instruction[0] + " is unknown. Please double check the guided path or"
              + " update ModelCheckingServerAbstract-transformInstructionToTransition function");
      return null;
    }

    return i.getRealTransition(this);
  }

  protected Transition nextInitialTransition() {

    if (directedInitialPath.size() == 0) {
      LOG.error("Initial Path Configuration is incorrect. Please check the target-sys.conf"
          + " and make sure that the initial path file exist.");
      System.exit(1);
    }
    LOG.info("DMCK next event execution is directed by initial path: "
        + directedInitialPath.get(directedInitialPathCounter));
    String command = directedInitialPath.get(directedInitialPathCounter);
    String[] instruction = command.split(" ");

    directedInitialPathCounter++;
    if (directedInitialPathCounter >= directedInitialPath.size()) {
      hasFinishedDirectedInitialPath = true;
    }

    // Experiment: Try to speed up DMCK execution by setting steadyStateTimeout to 0
    // and, instead of a limited iterations of for loop, set a while true loop until
    // the expected event is seen in DMCK queue.
    Transition transition = null;
    int retryCounter = 0;
    while (retryCounter < 20) {
      transition = transformInstructionToTransition(instruction);
      if (transition != null) {
        break;
      }
      retryCounter = waitForExpectedEvent(retryCounter);
    }

    if (transition instanceof SleepTransition) {
      return transition;
    } else if (transition == null) {
      throw new RuntimeException("Expected event cannot be found in DMCK Queue=" + command);
    }

    int id = -1;
    for (int i = 0; i < currentEnabledTransitions.size(); i++) {
      // replace abstract with real one based on id
      Transition eventInQueue = currentEnabledTransitions.get(i);
      if ((transition instanceof NodeCrashTransition
          && eventInQueue instanceof AbstractNodeCrashTransition)
          || (transition instanceof NodeStartTransition
              && eventInQueue instanceof AbstractNodeStartTransition)) {
        NodeOperationTransition nodeOp = (NodeOperationTransition) transition;
        AbstractNodeOperationTransition abstractNodeOpInQueue =
            (AbstractNodeOperationTransition) eventInQueue;
        nodeOp.setVectorClock(abstractNodeOpInQueue.getPossibleVectorClock(nodeOp.getId()));
        currentEnabledTransitions.set(i, transition);
        eventInQueue = currentEnabledTransitions.get(i);
      }
      if (transition.getTransitionId() == eventInQueue.getTransitionId()) {
        id = i;
        break;
      }
    }
    return currentEnabledTransitions.get(id);
  }

  // raft specific
  public void raftSnapshot(int leaderId) {
    RaftWorkloadDriver raftWD = (RaftWorkloadDriver) workloadDriver;
    raftWD.raftSnapshot(leaderId);
    try {
      LOG.debug("Wait for snapshot execution effect for " + snapshotWaitingTime + "ms");
      Thread.sleep(snapshotWaitingTime);
      waitNodeSteady(leaderId);
    } catch (InterruptedException e) {
      LOG.error("Error when waiting for Raft Snapshot steady state.");
      e.printStackTrace();
    }
  }

  protected boolean checkTerminationPoint(LinkedList<Transition> queue) {
    if (DMCK_NAME.equals("raftModelChecker")) {
      boolean isThereAnyHardCrash = false;
      for (LocalState ls : localStates) {
        if ((int) ls.getValue("state") == 3) {
          isThereAnyHardCrash = true;
          break;
        }
      }
      return queue.isEmpty() || isThereAnyHardCrash;
    } else if (DMCK_NAME.equals("zkChecker-ZAB")) {
      return queue.isEmpty() && numQueueInitWorkload <= 0;
    }

    return queue.isEmpty();
  }

  protected void verify() {
    startTimeVerification = new Timestamp(System.currentTimeMillis());
    boolean verifiedResult = verifier.verify();
    String detail = verifier.verificationDetail();
    saveResult(verifiedResult, detail);
    endTimeVerification = new Timestamp(System.currentTimeMillis());
  }

  protected void waitForNextLE() {
    // check if there is a leader but there is a node which is in
    // differentTerm
    int totalLeader = 0;
    int totalCandidate = 0;
    int totalFollower = 0;
    boolean diffTerm = false;
    waitForNextLE = false;
    for (int i = 0; i < numNode; i++) {
      if ((int) localStates[i].getValue("state") == 2) {
        totalLeader++;
      } else if ((int) localStates[i].getValue("state") == 1) {
        totalCandidate++;
      } else if ((int) localStates[i].getValue("state") == 0) {
        totalFollower++;
      } else if ((int) localStates[i].getValue("state") == -1) {
        // there is node that hasn't executed any event
        waitForNextLE = true;
      }

      LOG.info("Node " + i + " state: " + localStates[i].getRaftStateName() + " term: "
          + localStates[i].getValue("term"));
    }
    if (!allNodesHasTheSameTerm()) {
      diffTerm = true;
    }

    if (diffTerm && totalLeader == 1) {
      // check if there is a leader but the nodes are in different term,
      // then wait for next LE
      LOG.info("There is atleast one node in different term");
      waitForNextLE = true;
      waitedForNextLEInDiffTermCounter++;
    } else if (totalLeader > 1) {
      // check if there is more than one leader, then wait for next LE
      LOG.info("There are too many leaders");
      waitForNextLE = true;
    } else if (numNode == totalFollower + totalCandidate) {
      // check if all nodes are followers or candidates, then wait for
      // next LE
      LOG.info("There is no leader");
      waitForNextLE = true;
    } else {
      waitedForNextLEInDiffTermCounter = 0;
    }
  }

  protected boolean atleastEachNodeExecuteOnes() {
    if (DMCK_NAME.equals("raftModelChecker")) {
      int unsetNode = 0;
      for (int i = 0; i < numNode; i++) {
        if ((int) localStates[i].getValue("state") < 0) {
          unsetNode++;
        }
      }
      return unsetNode == 0;
    }
    return true;
  }

  protected boolean allNodesHasTheSameTerm() {
    if (DMCK_NAME.equals("raftModelChecker")) {
      for (int i = 0; i < numNode; i++) {
        if (i > 0
            && (int) localStates[i].getValue("term") != (int) localStates[i - 1].getValue("term")) {
          return false;
        }
      }
    }
    return true;
  }

  public boolean hasOneLeader() {
    if (DMCK_NAME.equals("raftModelChecker")) {
      int totalLeader = 0;
      for (int i = 0; i < numNode; i++) {
        LOG.info("[STATE] node-" + i + ": " + localStates[i].getValue("state"));
        if ((int) localStates[i].getValue("state") == 2) {
          totalLeader++;
        }
      }
      if (totalLeader == 1) {
        return true;
      }
    }
    return false;
  }

  abstract protected static class Explorer extends Thread {

    protected ModelCheckingServerAbstract dmck;

    public Explorer(ModelCheckingServerAbstract dmck) {
      this.dmck = dmck;
    }

  }

  protected LocalState[] copyLocalState(LocalState[] localStates) {
    LocalState[] copyLocalState = new LocalState[localStates.length];
    for (int i = 0; i < localStates.length; i++) {
      copyLocalState[i] = localStates[i].clone();
    }
    return copyLocalState;
  }

  public boolean hasNoMoreInterestingPath() {
    return hasFinishedAllExploration;
  }

  // Added for collecting stats for PCTCP and Random exploration
  protected FileRecorder recorder;
  protected long startTime;

  public void record(String s) {
    recorder.writeToFile(s);
  }

  public void recordTimeInfo() {
    long endTime = System.currentTimeMillis();
    record("Start time of the test: " + startTime);
    record("End time of the test: " + endTime);
    record("Elapsed time in msecs: " + (endTime - startTime));
    record("Elapsed time in secs: " + (double)(endTime - startTime)/1000);
    record("Elapsed time in mins: " + (double)(endTime - startTime)/60000 + "\n");
    recorder.closeFile();
  }
}
