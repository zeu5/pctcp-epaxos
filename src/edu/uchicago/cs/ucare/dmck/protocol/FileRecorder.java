package edu.uchicago.cs.ucare.dmck.protocol;

import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.time.LocalDateTime;

public class FileRecorder {

  protected BufferedWriter writer;

  protected FileRecorder() {
    try {
      writer = new BufferedWriter(new FileWriter("File" + LocalDateTime.now().toString() + ".txt"));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public FileRecorder(String fileName) {
    try {
      writer = new BufferedWriter(new FileWriter(fileName));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void writeToFile(String s) {
    try {
      writer.write(s.concat("\n"));
    } catch (IOException e1) {
      e1.printStackTrace();
    }
  }

  public void closeFile() {
     try {
        writer.close();
     } catch (IOException e) {
        e.printStackTrace();	
     }
  }

}
