package org.keyple.demo.apppc;

import org.eclipse.keyple.seproxy.SeReader;
import org.eclipse.keyple.seproxy.exception.KeypleReaderNotFoundException;

import java.util.ArrayList;
import java.util.List;

public class TicketingSessionManager {
    List<TicketingSession> ticketingSessions;
    private final SeReader samReader;

    TicketingSessionManager(SeReader samReader) {
        this.samReader = samReader;
        ticketingSessions = new ArrayList<TicketingSession>();
    }


    TicketingSession createTicketingSession(SeReader poReader) {
        TicketingSession ticketingSession = new TicketingSession(poReader, samReader);
        ticketingSessions.add(ticketingSession);
        return ticketingSession;
    }

    boolean destroyTicketingSession(String poReaderName) throws KeypleReaderNotFoundException {
        for(TicketingSession ticketingSession: ticketingSessions) {
            if(ticketingSession.getPoReader().getName().equals(poReaderName)) {
                ticketingSessions.remove(ticketingSession);
                return true;
            }
        }
        throw new KeypleReaderNotFoundException(poReaderName);
    }

    TicketingSession getTicketingSession(String poReaderName) throws KeypleReaderNotFoundException {
        for(TicketingSession ticketingSession: ticketingSessions) {
            if(ticketingSession.getPoReader().getName().equals(poReaderName)) {
                return ticketingSession;
            }
        }
        throw new KeypleReaderNotFoundException(poReaderName);
    }
}
