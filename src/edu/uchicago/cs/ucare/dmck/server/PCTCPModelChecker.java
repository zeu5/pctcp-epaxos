package edu.uchicago.cs.ucare.dmck.server;

import com.almworks.sqlite4java.SQLiteException;
import edu.uchicago.cs.ucare.dmck.transition.*;
import edu.uchicago.cs.ucare.dmck.util.ExploredBranchRecorder;
import edu.uchicago.cs.ucare.dmck.util.SqliteExploredBranchRecorder;
import edu.uchicago.cs.ucare.dmck.server.pctcp.*;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.LinkedList;

public class PCTCPModelChecker extends ModelCheckingServerAbstract {

  ExploredBranchRecorder exploredBranchRecorder;
  int numCrash;
  int numReboot;
  int currentCrash;
  int currentReboot;
  String stateDir;

  PCTCPScheduler scheduler;

  // PCTCP parameters files are read from a config file
  // The following are the default values
  long seed = 12345678;
  int maxMessages = 54;
  int bugDepth = 5; // bugDepth #events to order, bugDepth - 1 #priority-change-points in PCTCPScheduler
  int windowStartIndex = 0;
  int windowEndIndex = 53;
  int maxIterations = 1000;

  int numEvents = 0;
  int currentIteration = 0;
  double avgNumOfEvents = 0;
  double avgNumOfChains = 0;


  public PCTCPModelChecker(String dmckName, FileWatcher fileWatcher, int numNode, int numCrash,
                           int numReboot, String globalStatePathDir, String packetRecordDir, String workingDir,
                           WorkloadDriver workloadDriver, String ipcDir) {
    super(dmckName, fileWatcher, numNode, globalStatePathDir, workingDir, workloadDriver, ipcDir);
    this.numCrash = numCrash;
    this.numReboot = numReboot;
    stateDir = globalStatePathDir;
    try {
      exploredBranchRecorder = new SqliteExploredBranchRecorder(packetRecordDir);
    } catch (SQLiteException e) {
      LOG.error("", e);
    }

    readPCTCPConfig();

    StringBuilder sb = new StringBuilder("PCTCP - Started test with initial seed: ");
    sb.append(seed).append("\nMax messages: ").append(maxMessages)
        .append("  Window_start_index:").append(windowStartIndex).append("  Window_end_index: ").append(windowEndIndex)
        .append("\nNumber of iterations to run: ").append(maxIterations);
    record(sb.toString());
    LOG.info(sb.toString());
    resetTest();
  }

  private void readPCTCPConfig() {
    PCTCPConfig cfg = new PCTCPConfig();

    String seedStr = cfg.getProperty("pctcp.randomSeed");
    String maxMessagesStr = cfg.getProperty("pctcp.maxMessages");
    String bugDepthStr = cfg.getProperty("pctcp.bugDepth");
    String windowStartIndexStr = cfg.getProperty("pctcp.windowStartIndex");
    String windowEndIndexStr = cfg.getProperty("pctcp.windowEndIndex");
    String maxIterationsStr = cfg.getProperty("pctcp.maxIterations");

    if(seedStr != null)
      seed = Long.parseLong(seedStr);

    if(maxMessagesStr != null)
      maxMessages = Integer.parseInt(maxMessagesStr);

    if(bugDepthStr != null)
      bugDepth = Integer.parseInt(bugDepthStr);

    if(windowStartIndexStr != null)
      windowStartIndex = Integer.parseInt(windowStartIndexStr);

    if(windowEndIndexStr != null)
      windowEndIndex = Integer.parseInt(windowEndIndexStr);

    if(maxIterationsStr != null)
      maxIterations = Integer.parseInt(maxIterationsStr);

    String workloadPointsAllStr = cfg.getProperty("workloadPoints");
    String workloadFilesAll = cfg.getProperty("workloadFiles");

    if(workloadPointsAllStr != null) {
      String points[] = workloadPointsAllStr.split(",");
      workloadDriver.workloadPoints.clear();
      for(String s: points)
        workloadDriver.workloadPoints.add(Integer.parseInt(s));
    }

    if(workloadFilesAll != null) {
      workloadDriver.workloadFiles = Arrays.asList(workloadFilesAll.split(","));
    }

    if(workloadDriver.workloadPoints.size() != workloadDriver.workloadFiles.size()) {
      LOG.error("The size of the workload points and the number of workload files do not match, check the configuration file!");
      System.exit(-1);
    }
  }

  public void getDMCKConfig() {
    super.getDMCKConfig();
    readPCTCPConfig();
  }

  @Override
  public void resetTest() {
    if(currentIteration >= maxIterations) {
      DMCKRunner.checkIfBugReproduced();
      recordTimeInfo();
      System.exit(-1);
    }
    if (exploredBranchRecorder == null) {
      return;
    }
    super.resetTest();
    currentCrash = 0;
    currentReboot = 0;
    modelChecking = new PathTraversalWorker();
    currentEnabledTransitions = new LinkedList<Transition>();
    exploredBranchRecorder.resetTraversal();
    File waiting = new File(stateDir + "/.waiting");
    try {
      waiting.createNewFile();
    } catch (IOException e) {
      LOG.error("", e);
    }

    PCTCPOptions options = new PCTCPOptions(seed, maxMessages, bugDepth, windowStartIndex, windowEndIndex);
    scheduler = new PCTCPScheduler(options);
    numEvents = 0;
    seed += 1;
    currentIteration ++;
    initial = true;
    willRepeat = false;
  }

  boolean initial = true;
  boolean willRepeat = false;

  @SuppressWarnings("unchecked")
  public Transition nextTransition(LinkedList<Transition> transitions) {

    if(initial && currentEnabledTransitions.size() < 3) {
       System.out.println("Some messages have not arrived.. Will repeat the test with the same seed..");
       willRepeat = true;
       seed --;
       currentIteration --;
    }
    initial = false;

    int wl = workloadDriver.hasWorkloadAt(numEvents);
    if(wl != -1) workloadDriver.startWorkload(wl);

    LinkedList<Transition> cloneQueue = (LinkedList<Transition>) transitions.clone();
    scheduler.addNewEvents(cloneQueue);

    while (cloneQueue.size() > 0) {

      Transition scheduled = scheduler.scheduleNext();
      //LOG.info("PCTPC - Transition to schedule: " + scheduled);

      if(scheduled == null) {
       LOG.error("Scheduled is null. Scheduler data: " + scheduler.toString());
       for(Transition t: cloneQueue) {
         LOG.error(t.toString());
       }
       System.exit(-1);
      }
      Transition cloneTransition = null;

      for(Transition t: cloneQueue) {
          if(t.getTransitionId() == scheduled.getTransitionId()) {
            cloneTransition = t;
            cloneQueue.remove(t);
            break;
          }
      }

      if (cloneTransition != null && !exploredBranchRecorder.isSubtreeBelowChildFinished(cloneTransition.getTransitionId())) {
        numEvents++;
        return cloneTransition;
      }
    }

    return null;
  }

  protected void recordTestId() {
    exploredBranchRecorder.noteThisNode(".test_id", testId + "");
  }

  protected void adjustCrashAndReboot(LinkedList<Transition> enabledTransitions) {
    int numOnline = 0;
    for (int i = 0; i < numNode; ++i) {
      if (isNodeOnline(i)) {
        numOnline++;
      }
    }
    int numOffline = numNode - numOnline;
    int tmp = numOnline < numCrash - currentCrash ? numOnline : numCrash - currentCrash;
    for (int i = 0; i < tmp; ++i) {
      LOG.debug("DMCK add crash event");
      AbstractNodeCrashTransition crash = new AbstractNodeCrashTransition(this, true);
      for (int j = 0; j < numNode; ++j) {
        crash.setPossibleVectorClock(j, vectorClocks[j][numNode]);
      }
      enabledTransitions.add(crash);
      currentCrash++;
      numOffline++;
    }
    tmp = numOffline < numReboot - currentReboot ? numOffline : numReboot - currentReboot;
    for (int i = 0; i < tmp; ++i) {
      LOG.debug("DMCK add start event");
      AbstractNodeStartTransition start = new AbstractNodeStartTransition(this);
      for (int j = 0; j < numNode; ++j) {
        start.setPossibleVectorClock(j, vectorClocks[j][numNode]);
      }
      enabledTransitions.add(start);
      currentReboot++;
    }
  }

  class PathTraversalWorker extends Thread {

    @Override
    public void run() {
      boolean hasWaited = waitEndExploration == 0;
      while (true) {
        executeMidWorkload();
        updateSAMCQueueWithoutLog();
        boolean terminationPoint = checkTerminationPoint(currentEnabledTransitions); 
        if ((terminationPoint && hasWaited) /*|| (currentIteration > maxIterations)*/) {
          collectDebugData(localStates);
          LOG.info("---- End of Path Execution ----");

          // Performance evaluation to calculate path execution time.
          endTimePathExecution = new Timestamp(System.currentTimeMillis());
          collectPerformancePerEventMetrics();

          // Verification phase.
          verify();

          // Evaluation Phase.
          startTimeEvaluation = new Timestamp(System.currentTimeMillis());
          recordTestId();
          exploredBranchRecorder.markBelowSubtreeFinished();
          endTimeEvaluation = new Timestamp(System.currentTimeMillis());

          collectPerformancePerPathMetrics();
          LOG.info("---- End of Path Evaluation ----");
          //B
          //scheduler.printSchedule();
          if(!willRepeat) {
            recordRunInfo();
          }
          resetTest();
          break;
        } else if (terminationPoint) {
          try {
            if (DMCK_NAME.equals("raftModelChecker") && waitForNextLE
                && waitedForNextLEInDiffTermCounter < 20) {
              Thread.sleep(leaderElectionTimeout);
            } else {
              hasWaited = true;
              LOG.debug("Wait for any long process");
              Thread.sleep(waitEndExploration);
            }
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          continue;
        }
        hasWaited = waitEndExploration == 0;
        Transition nextEvent;
        // take next path based on initial path or current policy
        //boolean recordPath = true;
        //if (hasDirectedInitialPath && !hasFinishedDirectedInitialPath) {
        //  nextEvent = nextInitialTransition();
        //  recordPath = false;
        //} else {
          nextEvent = nextTransition(currentEnabledTransitions); //BB PCT decides!
        //}
        if (nextEvent != null) {
          // Collect Logs
          collectDebugData(localStates);
        //  if (recordPath) {
            exploredBranchRecorder.createChild(nextEvent.getTransitionId());
            exploredBranchRecorder.traverseDownTo(nextEvent.getTransitionId());
            exploredBranchRecorder.noteThisNode(".packets", nextEvent.toString(), false);
        //  }

          // Transform abstract event to real event.
          if (nextEvent instanceof AbstractNodeOperationTransition) {
            AbstractNodeOperationTransition nodeOperationTransition =
                (AbstractNodeOperationTransition) nextEvent;
            nextEvent =
                ((AbstractNodeOperationTransition) nextEvent).getRealNodeOperationTransition();
            if (nextEvent == null) {
              currentEnabledTransitions.add(nodeOperationTransition);
              continue;
            }
            nodeOperationTransition.setId(((NodeOperationTransition) nextEvent).getId());
          }
          collectDebugNextTransition(nextEvent);

          // Remove real next event from DMCK queue
          removeEventFromQueue(currentEnabledTransitions, nextEvent);

          if (nextEvent.apply()) {
            recordEventToPathFile(nextEvent.toString());
            updateSAMCQueueAfterEventExecution(nextEvent);
          }
        } else if (exploredBranchRecorder.getCurrentDepth() == 0) {
          hasFinishedAllExploration = true;
        } else {
          recordEventToPathFile("Duplicated path.\n");
          resetTest();
          break;
        }
      }
    }
  }

  public void recordRunInfo() {

    int numEvents = scheduler.getSchedule().size();

    if(numEvents == 0) { // no messages arrives
      System.out.println("No messages have not arrived.. Will repeat the test with the same seed..");
      seed --;
      currentIteration --;

    } else {
      recorder.writeToFile(scheduler.toString());

      avgNumOfEvents += numEvents;
      avgNumOfChains += scheduler.getMaxNumAvailableChains();

      recorder.writeToFile("Last scheduled number of events: " + numEvents);
      recorder.writeToFile("Average number of events: " + (avgNumOfEvents / currentIteration));
      recorder.writeToFile("Average number of max chains: " + (avgNumOfChains / currentIteration));
      recorder.writeToFile("Number of iterations: " + currentIteration + "\n\n");
    }
  }

}

