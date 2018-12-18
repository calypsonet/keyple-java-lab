package org.cna.keyple.demo;

import org.eclipse.keyple.plugin.remotese.pluginse.VirtualReaderService;

public class TicketServiceMock implements TicketService {



    TicketServiceMock(){
    }

    @Override
    public Integer increaseTicketNumber(Integer ticketNumber, String sessionId) {
        return 2;
    }

    @Override
    public Integer readTicketNumber(String sessionId) {
        return 1;
    }

    @Override
    public Integer decreaseTicketNumber(Integer ticketNumber, String sessionId) {
        return 0;
    }
}
