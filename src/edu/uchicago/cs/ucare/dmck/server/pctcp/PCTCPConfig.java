package edu.uchicago.cs.ucare.dmck.server.pctcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Properties;

public class PCTCPConfig
{
  protected static Logger LOG = LoggerFactory.getLogger(PCTCPConfig.class);
  private Properties configFile;
  private String configFileName = "pctcp.conf";

  public PCTCPConfig()
  {

    configFile = new java.util.Properties();

    File f = new File(configFileName);
    if(!f.exists() || f.isDirectory()) {
      LOG.error("The configuration file pctcp.conf is not found.\n");
      System.exit(-1);
    }

    try {
      configFile.load(this.getClass().getClassLoader().
          getResourceAsStream(configFileName));
    }catch(Exception e){
      e.printStackTrace();
    }
  }

  public String getProperty(String key)
  {
    return this.configFile.getProperty(key);
  }
}
