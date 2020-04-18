package edu.uchicago.cs.ucare.dmck.epaxos;

import java.io.PrintWriter;
import java.util.Properties;

import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.server.FileWatcher;
import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;

public class EpaxosFileWatcher extends FileWatcher {
    
    private static String FILE_PREFIX = "epaxos_message_";
    
    public EpaxosFileWatcher(String filePath, ModelCheckingServerAbstract dmck) {
        super(filePath, dmck);
    }

    public void proceedEachFile(String filename, Properties ev){

        try {
            if (filename.startsWith(FILE_PREFIX)) {
                long eventid = Long.parseLong(ev.getProperty("eventId"));
                int sender = Integer.parseInt(ev.getProperty("sender"));
                int recv = Integer.parseInt(ev.getProperty("recv"));

                Event event = new Event(commonHashId(eventid));
                event.addKeyValue(Event.FILENAME, filename);
                event.addKeyValue(Event.FROM_ID, sender);
                event.addKeyValue(Event.TO_ID, recv);
                event.addKeyValue("eventId", eventid);
                event.addKeyValue("verb", ev.getProperty("msgtype"));
                event.addKeyValue("msg", ev.getProperty("msg"));
                event.setVectorClock(dmck.getVectorClock(sender, recv));

                dmck.offerPacket(event);
            }
        } catch (Exception e) {
            LOG.error("Error accepting file");
        }
    }

    protected void sequencerEnablingSignal(Event e) {
        commonEnablingSignal(e);
    }

    public void commonEnablingSignal(Event ev) {
        try {
            PrintWriter writer =
                    new PrintWriter(ipcDir + "/new/" + ev.getValue(Event.FILENAME), "UTF-8");
            writer.println("eventId=" + ev.getValue("eventId"));
            writer.println("execute=true");
            writer.close();

            Runtime.getRuntime().exec("mv " + ipcDir + "/new/" + ev.getValue(Event.FILENAME) + " "
                    + ipcDir + "/ack/" + ev.getValue(Event.FILENAME));
        } catch (Exception e) {
            LOG.error("Error when enabling event in common way=" + e.toString());
        }
    }
}