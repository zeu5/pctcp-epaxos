package edu.uchicago.cs.ucare.dmck.epaxos;

import java.util.Properties;

import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.server.FileWatcher;
import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;

class EpaxosFileWatcher extends FileWatcher {
    
    public EpaxosFileWatcher(String filePath, ModelCheckingServerAbstract dmck) {
        super(filePath, dmck);
        // Initialise the driver.
    }

    @Override
    public void run(){
        // Main loop where you listen to intercepted messages and pass it onto PCT
        // - acceptFile is the enabling flag
        // - commonHashId needs to be called when you record a new event
    }

    public void proceedEachFile(String filename, Properties ev){
        // Dummy method which will not be of any use for us. Don't want to deal with files.
    }

    protected void sequencerEnablingSignal(Event e) {
        // This is where you get the next event that needs to be scheduled
    }

    @Override
    public void commonEnablingSignal(Event e) {
        // Also enables an event to be scheduled
    }
}