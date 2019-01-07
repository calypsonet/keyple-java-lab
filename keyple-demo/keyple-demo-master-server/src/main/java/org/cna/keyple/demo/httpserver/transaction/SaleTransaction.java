package org.cna.keyple.demo.httpserver.transaction;

import org.cna.keyple.demo.ticketing.CardContent;

import java.util.Date;

public class SaleTransaction {

    public static int WAIT_FOR_NEW_CARD = 0;
    public static int WAIT_FOR_CARD_TO_LOAD = 1;

    String id;
    int status;
    CardContent cardContent;
    Integer ticketNumber; //ticket number to load
    String poReader;

    public SaleTransaction(String poReader) {
        this.poReader = poReader;
        this.id = poReader +"-"+ new Date().getTime();
    }

    public String getPoReader() {
        return poReader;
    }

    public void setPoReader(String poReader) {
        this.poReader = poReader;
    }



    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Integer getTicketNumber() {
        return ticketNumber;
    }

    public void setTicketNumber(Integer ticketNumber) {
        this.ticketNumber = ticketNumber;
    }

    public CardContent getCardContent() {
        return cardContent;
    }

    public void setCardContent(CardContent cardContent) {
        this.cardContent = cardContent;
    }
}
