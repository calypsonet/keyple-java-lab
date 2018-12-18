package org.cna.keyple.demo;

import org.eclipse.keyple.plugin.remotese.pluginse.VirtualReaderService;
import org.eclipse.keyple.seproxy.SeReader;

public class TicketServiceImpl implements TicketService {

    private SeReader poReader, samReader;

    TicketServiceImpl(SeReader poReader, SeReader samReader){
        this.poReader = poReader;
        this.samReader = samReader;
    }

    @Override
    public Integer increaseTicketNumber(Integer ticketNumber, String sessionId) {

        //todo
        return 2;
    }

    @Override
    public Integer readTicketNumber(String sessionId) {
        //todo
        return 1;
    }

    @Override
    public Integer decreaseTicketNumber(Integer ticketNumber, String sessionId) {
        //todo
        return 0;
    }
}
