package edu.uchicago.cs.ucare.dmck.epaxos;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import com.google.gson.JsonObject;

import edu.uchicago.cs.ucare.dmck.server.WorkloadDriver;

public class EpaxosWorkloadDriver extends WorkloadDriver {

    private Path epaxosPath;
    private Path binPath;
    private Path controllerConfPath;
    private Path logBasePath;
    private Path logPath;

    private Process masterProcess;
    private Process[] replicaProcesses;
    private Process workloadProcess;

    public EpaxosWorkloadDriver(
        int numNode, 
        String workingDir, 
        String ipcDir, 
        String samcDir,
        String targetSysDir) {
        super(numNode, workingDir, ipcDir, samcDir, targetSysDir);

        initialise();
    }

    private void initializeTest(int testid){
        this.logPath = Paths.get(this.logBasePath.toString(), "test_" + Integer.toString(testid));
        new File(this.logPath.toString()).mkdirs();
    }

    private void initialise(){
        // Get dmck config. Fetch path for Epaxos binaries.
        Path dmckConfigPath = Paths.get(workingDir, "dmck.conf");
        Properties dmckConf = new Properties();
        try {
            FileInputStream dmckConfigFile = new FileInputStream(dmckConfigPath.toString());
            dmckConf.load(dmckConfigFile);
            dmckConfigFile.close();

            this.epaxosPath = Paths.get(dmckConf.getProperty("epaxos_path"));
            this.logBasePath = Paths.get(dmckConf.getProperty("epaxos_log_path"));
            this.binPath = Paths.get(this.epaxosPath.toString(), "bin");
        } catch (Exception e) {
            LOG.error("Error reading dmck config");
        }

        // Create a json config path for the epaxos master controller
        JsonObject controllerConf = new JsonObject();
        controllerConf.addProperty("working_dir", ipcDir);

        this.controllerConfPath = Paths.get(workingDir, "controllerconf.json");

        List<String> lines = Arrays.asList(controllerConf.toString());
        try {
            Files.write(this.controllerConfPath, lines);
        } catch (Exception e) {
            LOG.error("Could not create epaxos controller conf");
        }

        this.replicaProcesses = new Process[numNode];
        initializeTest(this.testId);
    }

    @Override
    public void startEnsemble() {

        // Starting the master process
        try {
            ProcessBuilder pb = new ProcessBuilder(
                new String[]{"epaxosmaster", "-N", Integer.toString(this.numNode), "-intercept", "-conf", this.controllerConfPath.toString()}
            );
            pb.directory(new File(this.binPath.toString()));

            File logFile = new File(Paths.get(this.logPath.toString(),"master_log").toString());
            pb.redirectErrorStream(true);
            pb.redirectOutput(logFile);

            this.masterProcess = pb.start();
        } catch (Exception e) {
            LOG.error("Error starting master node");
        }
        super.startEnsemble();
    }

    @Override
    public void stopEnsemble() {
        this.masterProcess.destroy();
        super.stopEnsemble();
    }

    @Override
    public void startWorkload() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                new String[]{"workloadclient"}
            );
            pb.directory(new File(this.binPath.toString()));

            File logFile = new File(Paths.get(this.logPath.toString(),"workload_log").toString());
            pb.redirectErrorStream(true);
            pb.redirectOutput(logFile);

            this.workloadProcess = pb.start();
        } catch (Exception e) {
            LOG.error("Error starting workload");
        }
    }

    @Override
    public void startNode(int id) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                new String[]{"server", "-e", "-port", Integer.toString(7070+id)}
            );
            pb.directory(new File(this.binPath.toString()));

            File logFile = new File(Paths.get(this.logPath.toString(),"replica_"+Integer.toString(id)+"_log").toString());
            pb.redirectErrorStream(true);
            pb.redirectOutput(logFile);

            this.replicaProcesses[id] = pb.start();
        } catch (Exception e) {
            LOG.error("Error starting replica");
        }
    }

    @Override
    public void stopWorkload() {
        this.workloadProcess.destroy();
    }

    @Override
    public void stopNode(int id) {
        this.replicaProcesses[id].destroy();
    }

    @Override
    public void resetTest(int test_id) {
        clearIPCDir();
        this.testId = test_id;
        initializeTest(this.testId);
    }
}