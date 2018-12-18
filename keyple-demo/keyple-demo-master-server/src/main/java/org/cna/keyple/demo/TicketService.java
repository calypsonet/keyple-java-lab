package org.cna.keyple.demo;

public interface TicketService {


    /**
     * @param ticketNumber : ticket number to increase
     * @param sessionId :
     * @return available tickets in card after increase
     */
    Integer increaseTicketNumber(Integer ticketNumber, String sessionId);

    /**
     *
     * @param sessionId
     * @return available tickets in card
     */
    Integer readTicketNumber(String sessionId);

    /**
     * @param ticketNumber : ticket number to decrease
     * @param sessionId
     * @return available tickets in card after decrease
     */
    Integer decreaseTicketNumber(Integer ticketNumber, String sessionId);



}
