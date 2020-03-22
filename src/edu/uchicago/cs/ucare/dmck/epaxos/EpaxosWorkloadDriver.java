package edu.uchicago.cs.ucare.dmck.epaxos;

import edu.uchicago.cs.ucare.dmck.server.WorkloadDriver;

class EpaxosWorkloadDriver extends WorkloadDriver {

    public EpaxosWorkloadDriver(
        int numNode, 
        String workingDir, 
        String ipcDir, 
        String samcDir,
        String targetSysDir) {
        super(numNode, workingDir, ipcDir, samcDir, targetSysDir);

        initialise();
    }

    private void initialise(){
        // Get dmck config. Fetch URL for Epaxos master node and connect to it.
    }

    @Override
    public void startWorkload() {
        
    }

    @Override
    public void startNode(int id) {
        
    }

    @Override
    public void stopWorkload() {

    }

    @Override
    public void stopNode(int id) {
        
    }

    @Override
    public void resetTest(int test_id) {
        // No clue what this is supposed to do
    }


}