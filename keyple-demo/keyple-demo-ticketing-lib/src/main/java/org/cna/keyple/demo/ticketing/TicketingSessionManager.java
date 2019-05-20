package org.cna.keyple.demo.ticketing;

import org.eclipse.keyple.calypso.transaction.SamResource;
import org.eclipse.keyple.core.seproxy.SeReader;
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TicketingSessionManager {

    private final Logger logger = LoggerFactory.getLogger(TicketingSessionManager.class);


    List<TicketingSession> ticketingSessions;
    private final SamResource samResource;

    public TicketingSessionManager(SamResource samResource) {
        logger.info("Initialize TicketingSessionManager");
        this.samResource = samResource;
        ticketingSessions = new ArrayList<TicketingSession>();
    }


    public TicketingSession createTicketingSession(SeReader poReader) {
        logger.debug("Create a new TicketingSession for reader {}", poReader);
        TicketingSession ticketingSession = new TicketingSession(poReader, samResource);
        ticketingSessions.add(ticketingSession);
        return ticketingSession;
    }

    public boolean destroyTicketingSession(String poReaderName) throws KeypleReaderNotFoundException {
        logger.debug("Destroy a the TicketingSession for reader {}", poReaderName);
        for(TicketingSession ticketingSession: ticketingSessions) {
            if(ticketingSession.getPoReader().getName().equals(poReaderName)) {
                ticketingSessions.remove(ticketingSession);
                return true;
            }
        }
        throw new KeypleReaderNotFoundException(poReaderName);
    }

    public TicketingSession getTicketingSession(String poReaderName){
        logger.debug("Retrieve the TicketingSession of reader {}", poReaderName);

        for(TicketingSession ticketingSession: ticketingSessions) {
            if(ticketingSession.getPoReader().getName().equals(poReaderName)) {
                logger.debug("TicketingSession found for reader {}", poReaderName);
                return ticketingSession;
            }
        }
        logger.debug("No TicketingSession found for reader {}", poReaderName);
        return null;
    }

    public List<TicketingSession> findAll(){
        return ticketingSessions;
    }
}
