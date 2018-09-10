package edu.uchicago.cs.ucare.dmck.protocol.event;

import edu.uchicago.cs.ucare.dmck.protocol.FileRecorder;
import edu.uchicago.cs.ucare.dmck.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.dmck.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TraceRecorder extends FileRecorder {

  private List<Event> events;
  private Map<Long, Event> hashIdToEvent = new HashMap<Long, Event>();
  private List<String> keys = new ArrayList<String>();

  public TraceRecorder(String fileName) {
    super(fileName);
    events = new ArrayList<Event>();
  }

  // parts of message content to store
  public TraceRecorder(String fileName, List<String> keysToRecord) {
    super(fileName);
    events = new ArrayList<Event>();
    if(keysToRecord != null)
      keys = keysToRecord;
  }

  // Gets a list of events - sent messages, possible start/stop nodes
  // Filter the new events from the parameters and add into events list
  public List<Event> addSendEvents(List<Transition> availableTransitions) {
    List<Event> newEvents = new ArrayList<Event>();
    // collect send events only !
    for(Transition t: availableTransitions) {
      if(hashIdToEvent.get(t.getTransitionId()) == null && t instanceof PacketSendTransition) {
        Event e = transitionToEvent(t);
        newEvents.add(e);
        hashIdToEvent.put(e.getHashId(), e);
      }
    }

    return newEvents;
  }

  // adds a single event
  public Event addReceiveOrNodeEvent(Transition transition) {
    Event e = transitionToEvent(transition);
    if(!events.contains(e)) events.add(e);
    return e;
  }

  public void recordReceiveEvent(PacketSendTransition transition) {
    PacketSendTransition pst = (PacketSendTransition) transition;
    StringBuilder contentToRecord = new StringBuilder();
    for(String k: keys)
      contentToRecord.append(pst.getPacket().getValue(k) + " ");
    recordEvent(new MessageReceived(pst.getTransitionId(), String.valueOf(pst.getPacket().getFromId()),
        String.valueOf(pst.getPacket().getToId()), contentToRecord.toString()));
  }

  public void recordEvent(Event event) {
    try {
      writer.write(event.toJson().toString().concat("\n"));
    } catch (IOException e1) {
      e1.printStackTrace();
    }
  }

  public void recordEvents(List<Event> events) {
    for(Event e: events) {
      try {
        writer.write(e.toJson().toString().concat("\n"));
      } catch (IOException e1) {
        e1.printStackTrace();
      }
    }
  }


  public List<Event> getEvents(List<Transition> transitions) {
    List<Event> events = new ArrayList<Event>();
    for(Transition t: transitions) {
      events.add(transitionToEvent(t));
    }
    return events;
  }

  //todo add key to select what of packet will be kept in the content
  public Event transitionToEvent(Transition t) {

    if(t instanceof PacketSendTransition) {
      PacketSendTransition pst = (PacketSendTransition) t;
      StringBuilder contentToRecord = new StringBuilder();
      for(String k: keys)
        contentToRecord.append(pst.getPacket().getValue(k) + " ");
      return new MessageSent(pst.getTransitionId(), String.valueOf(pst.getPacket().getFromId()),
          String.valueOf(pst.getPacket().getToId()), contentToRecord.toString());
    } else if(t instanceof NodeStartTransition) {
      NodeStartTransition nst = (NodeStartTransition) t;
      return new NodeStarted(nst.getTransitionId(), String.valueOf(nst.getId()));
    } else if (t instanceof NodeCrashTransition) {
      NodeCrashTransition nct =(NodeCrashTransition) t;
      return new NodeStopped(nct.getTransitionId(), String.valueOf(nct.getId()));
    }

    System.out.println("Transition is not converted to event: " + t.toString());
    return null;
  }


}
