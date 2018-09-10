package edu.uchicago.cs.ucare.dmck.server.pctcp;

public class PCTCPOptions {

  private long randomSeed = System.currentTimeMillis();
  private int maxMessages = 0;
  private int bugDepth = 0;
  private int windowStartIndex = 0; // hint - starting index for a possible priority change point
  private int windowEndIndex = 0; // hint - ending index for a possible priority change point

  public PCTCPOptions(long randomSeed, int maxMessages, int bugDepth, int windowStartIndex, int windowEndIndex) {
    this.randomSeed = randomSeed;
    this.maxMessages = maxMessages;
    this.bugDepth = bugDepth;
    this.windowStartIndex = windowStartIndex;
    this.windowEndIndex = windowEndIndex;
  }

  public PCTCPOptions(long randomSeed, int maxMessages, int bugDepth) {
    this.randomSeed = randomSeed;
    this.maxMessages = maxMessages;
    this.bugDepth = bugDepth;
    this.windowStartIndex = 0;
    this.windowEndIndex = maxMessages;
  }

  public PCTCPOptions(int maxMessages, int bugDepth) {
    this.maxMessages = maxMessages;
    this.bugDepth = bugDepth;
    this.windowStartIndex = 0;
    this.windowEndIndex = maxMessages;
  }

  public PCTCPOptions(String fileName) {
    this.maxMessages = maxMessages;
    this.bugDepth = bugDepth;
    this.windowStartIndex = 0;
    this.windowEndIndex = maxMessages;
  }

  public long getRandomSeed() {
    return randomSeed;
  }

  public int getMaxMessages() {
    return maxMessages;
  }

  public int getWindowStartIndex() {
    return windowStartIndex;
  }

  public int getWindowEndIndex() {
    return windowEndIndex;
  }

  public int getBugDepth() {
    return bugDepth;
  }

  public String toString() {
    return "Random Seed: " + randomSeed + "\n" + "Max # of Messages: " + maxMessages + "\n"
        + "Bug depth: " + bugDepth + "\n";
  }
}
