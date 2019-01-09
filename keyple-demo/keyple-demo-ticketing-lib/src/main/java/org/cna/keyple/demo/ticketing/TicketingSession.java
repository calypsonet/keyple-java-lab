package org.cna.keyple.demo.ticketing;

import org.eclipse.keyple.calypso.command.po.parser.ReadDataStructure;
import org.eclipse.keyple.calypso.command.po.parser.ReadRecordsRespPars;
import org.eclipse.keyple.calypso.transaction.CalypsoPo;
import org.eclipse.keyple.calypso.transaction.PoSelector;
import org.eclipse.keyple.calypso.transaction.PoTransaction;
import org.eclipse.keyple.seproxy.ChannelState;
import org.eclipse.keyple.seproxy.SeReader;
import org.eclipse.keyple.seproxy.event.ObservableReader;
import org.eclipse.keyple.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.seproxy.protocol.ContactlessProtocols;
import org.eclipse.keyple.seproxy.protocol.TransmissionMode;
import org.eclipse.keyple.transaction.MatchingSe;
import org.eclipse.keyple.transaction.SeSelection;
import org.eclipse.keyple.transaction.SeSelector;
import org.eclipse.keyple.transaction.SelectionResponse;
import org.eclipse.keyple.util.ByteArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class TicketingSession {
    public final static int STATUS_OK = 0;
    public final static int STATUS_UNKNOWN_ERROR = 1;
    public final static int STATUS_CARD_SWITCHED = 2;
    public final static int STATUS_SESSION_ERROR = 3;
    private MatchingSe mifareClassic, mifareDesfire, bankingCard;
    private CalypsoPo calypsoPo;
    private SeSelection seSelection;
    private ReadRecordsRespPars readEventLogParser, readEnvironmentHolderParser, readCounterParser, readContractParser;
    private String poTypeName;
    private CardContent cardContent;
    private final SeReader poReader, samReader;
    private byte[] currentPoSN;
    final Logger logger = (Logger) LoggerFactory.getLogger(TicketingSession.class);

    private String pad(String text, char c, int length) {
        StringBuffer sb = new StringBuffer(length);
        sb.append(text);
        for(int i=text.length(); i<length; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    public TicketingSession(SeReader poReader, SeReader samReader) {
        this.poReader = poReader;
        this.samReader = samReader;
        this.cardContent = new CardContent();

        prepareAndSetPoDefaultSelection();
    }

    public SeReader getPoReader() {
        return poReader;
    }

    /**
     * prepare the default selection
     */
    private void prepareAndSetPoDefaultSelection() {
        /*
         * Prepare a PO selection
         */
        seSelection = new SeSelection(poReader);

        /* Select Calypso */
        PoSelector calypsoPoSelector = new PoSelector(ByteArrayUtils.fromHex(CalypsoInfo.AID),
                SeSelector.SelectMode.FIRST, ChannelState.KEEP_OPEN,
                ContactlessProtocols.PROTOCOL_ISO14443_4, "AID: " + CalypsoInfo.AID);

        readEnvironmentHolderParser = calypsoPoSelector.prepareReadRecordsCmd(CalypsoInfo.SFI_EnvironmentAndHolder,
                ReadDataStructure.SINGLE_RECORD_DATA, CalypsoInfo.RECORD_NUMBER_1,
                String.format("EnvironmentHolder (SFI=%02X))",
                        CalypsoInfo.SFI_EnvironmentAndHolder));

        readContractParser = calypsoPoSelector.prepareReadRecordsCmd(CalypsoInfo.SFI_Contracts,
                ReadDataStructure.SINGLE_RECORD_DATA, CalypsoInfo.RECORD_NUMBER_1,
                String.format("Contracts#1 (SFI=%02X))",
                        CalypsoInfo.SFI_Contracts));

        readCounterParser = calypsoPoSelector.prepareReadRecordsCmd(CalypsoInfo.SFI_Counter,
                ReadDataStructure.MULTIPLE_COUNTER, CalypsoInfo.RECORD_NUMBER_1,
                String.format("Counter (SFI=%02X))",
                        CalypsoInfo.SFI_Counter));

        readEventLogParser = calypsoPoSelector.prepareReadRecordsCmd(CalypsoInfo.SFI_EventLog,
                ReadDataStructure.MULTIPLE_RECORD_DATA, CalypsoInfo.RECORD_NUMBER_1,
                String.format("EventLog (SFI=%02X))",
                        CalypsoInfo.SFI_EventLog));

        /*
         * Add the selection case to the current selection (we could have added other cases here)
         */
        calypsoPo = (CalypsoPo) seSelection.prepareSelection(calypsoPoSelector);

        /* Select Mifare Classic PO */
        SeSelector mifareClassicSelector = new SeSelector(".*", ChannelState.CLOSE_AFTER,
                ContactlessProtocols.PROTOCOL_MIFARE_CLASSIC, "Mifare classic");

        /*
         * Add the selection case to the current selection (we could have added other cases here)
         */
        mifareClassic = (MatchingSe) seSelection.prepareSelection(mifareClassicSelector);

        /* Select Mifare Desfire PO */
        SeSelector mifareDesfireSelector = new SeSelector(".*", ChannelState.CLOSE_AFTER,
                ContactlessProtocols.PROTOCOL_MIFARE_DESFIRE, "Mifare desfire");

        /*
         * Add the selection case to the current selection (we could have added other cases here)
         */
        mifareDesfire = (MatchingSe) seSelection.prepareSelection(mifareDesfireSelector);

        SeSelector bankingSelector = new SeSelector(ByteArrayUtils.fromHex("325041592e5359532e4444463031"),
                SeSelector.SelectMode.FIRST, ChannelState.CLOSE_AFTER,
                ContactlessProtocols.PROTOCOL_ISO14443_4, "Visa");

        bankingCard = (MatchingSe) seSelection.prepareSelection(bankingSelector);

        /*
         * Provide the SeReader with the selection operation to be processed when a PO is inserted.
         */
        ((ObservableReader) poReader).setDefaultSelectionRequest(
                seSelection.getSelectionOperation(),
                ObservableReader.NotificationMode.ALWAYS);
    }

    public boolean processDefaultSelection(SelectionResponse selectionResponse) {
        boolean selectionStatus;
        if(calypsoPo == null) {
            logger.error("calypsoPo is null.");
        }
        if(mifareClassic == null) {
            logger.error("mifareClassic is null.");
        }
        if(mifareDesfire == null) {
            logger.error("mifareDesfire is null.");
        }
        if(bankingCard == null) {
            logger.error("bankingCard is null.");
        }
        logger.info("selectionResponse");
        logger.info("selectionResponse = {}", selectionResponse);
        selectionStatus = seSelection.processDefaultSelection(selectionResponse);
        if(calypsoPo.isSelected()) {
            poTypeName = "CALYPSO";
        } else if(mifareClassic.getSelectionSeResponse() != null && mifareClassic.getSelectionSeResponse().getSelectionStatus().hasMatched()) {
            poTypeName = "MIFARE Classic";
        } else if(mifareDesfire.getSelectionSeResponse() != null && mifareDesfire.getSelectionSeResponse().getSelectionStatus().hasMatched()) {
            poTypeName = "MIFARE Desfire";
        } else if(bankingCard.getSelectionSeResponse() != null &&  bankingCard.getSelectionSeResponse().getSelectionStatus().hasMatched()) {
            poTypeName = "EMV";
        } else {
            poTypeName = "OTHER";
        }
        logger.info("PO type = {}", poTypeName);
        return selectionStatus;
    }

    public String getPoTypeName() {
        return poTypeName;
    }

    public CardContent getCardContent() {
        return cardContent;
    }

    public String getPoIdentification() {
        return ByteArrayUtils.toHex(calypsoPo.getApplicationSerialNumber()) + ", " + calypsoPo.getRevision().toString();
    }

    /**
     * do the personalization of the PO according to the specified profile
     * @param profile
     * @return
     */
    public boolean personalize(String profile) {
        PoTransaction poTransaction = new PoTransaction(poReader, calypsoPo,
                samReader, CalypsoInfo.getSamSettings());

        boolean poProcessStatus = false;
        try {
            poProcessStatus = poTransaction.processOpening(
                    PoTransaction.ModificationMode.ATOMIC,
                    PoTransaction.SessionAccessLevel.SESSION_LVL_PERSO, (byte) 0, (byte) 0);
        } catch (KeypleReaderException e) {
            e.printStackTrace();
        }

        if ("PROFILE1".equals(profile)) {
            poTransaction.prepareUpdateRecordCmd(CalypsoInfo.SFI_EnvironmentAndHolder, CalypsoInfo.RECORD_NUMBER_1,
                    pad("John Smith", ' ', 29).getBytes(), "HolderName: John Smith");
            poTransaction.prepareUpdateRecordCmd(CalypsoInfo.SFI_Contracts, CalypsoInfo.RECORD_NUMBER_1,
                    pad("NO CONTRACT", ' ', 29).getBytes(), "Contract: NO CONTRACT");
        } else {
            poTransaction.prepareUpdateRecordCmd(CalypsoInfo.SFI_EnvironmentAndHolder, CalypsoInfo.RECORD_NUMBER_1,
                    pad("Harry Potter", ' ', 29).getBytes(), "HolderName: Harry Potter");
            poTransaction.prepareUpdateRecordCmd(CalypsoInfo.SFI_Contracts, CalypsoInfo.RECORD_NUMBER_1,
                    pad("1 MONTH SEASON TICKET", ' ', 29).getBytes(), "Contract: 1 MONTH SEASON TICKET");
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
        String dateTime = LocalDateTime.now().format(formatter);

        poTransaction.prepareAppendRecordCmd(CalypsoInfo.SFI_EventLog,
                pad(dateTime + " OP = PERSO", ' ', 29).getBytes(),
                "Event: blank");

        poTransaction.prepareUpdateRecordCmd(CalypsoInfo.SFI_Counter, CalypsoInfo.RECORD_NUMBER_1, ByteArrayUtils
                .fromHex(pad("", '0', 29*2)), "Reset all counters");

        try {
            poProcessStatus = poTransaction.processClosing(TransmissionMode.CONTACTLESS,
                    ChannelState.CLOSE_AFTER);
        } catch (KeypleReaderException e) {
            e.printStackTrace();
        }

        return poProcessStatus;
    }

    /**
     * load the PO according to the choice provided as an argument
     * @param ticketNumber
     * @return
     * @throws KeypleReaderException
     */
    public int loadTickets(int ticketNumber) throws KeypleReaderException {
        PoTransaction poTransaction = new PoTransaction(poReader, calypsoPo,
                samReader, CalypsoInfo.getSamSettings());

        if(!Arrays.equals(currentPoSN, calypsoPo.getApplicationSerialNumber())) {
            return STATUS_CARD_SWITCHED;
        }

        boolean poProcessStatus = false;
        poProcessStatus = poTransaction.processOpening(
                PoTransaction.ModificationMode.ATOMIC,
                PoTransaction.SessionAccessLevel.SESSION_LVL_LOAD, (byte) 0, (byte) 0);

        if(!poProcessStatus) {
            return STATUS_SESSION_ERROR;
        }

        /* allow to determine the anticipated response */
        poTransaction.prepareReadRecordsCmd(CalypsoInfo.SFI_Counter,
                ReadDataStructure.MULTIPLE_COUNTER, CalypsoInfo.RECORD_NUMBER_1,
                String.format("Counter (SFI=%02X))",
                        CalypsoInfo.SFI_Counter));
        poTransaction.processPoCommands(ChannelState.KEEP_OPEN);
        poTransaction.prepareIncreaseCmd(CalypsoInfo.SFI_Counter, (byte) 0x01, ticketNumber, "Increase " + ticketNumber);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd HH:mm:ss");
        String dateTime = LocalDateTime.now().format(formatter);

        String event = "";
        if(ticketNumber > 0) {
            event = pad(dateTime + " OP = +" + ticketNumber, ' ', 29);
        } else {
            event = pad(dateTime + " T1", ' ', 29);
        }

        poTransaction.prepareAppendRecordCmd(CalypsoInfo.SFI_EventLog, event.getBytes(), "Event: " + event);

        poProcessStatus = poTransaction.processClosing(TransmissionMode.CONTACTLESS,
                ChannelState.CLOSE_AFTER);

        if(!poProcessStatus) {
            return STATUS_SESSION_ERROR;
        }

        return STATUS_OK;
    }


    /**
     * Load a season ticket contract
     * @return
     * @throws KeypleReaderException
     */
    public int loadContract() throws KeypleReaderException {
        PoTransaction poTransaction = new PoTransaction(poReader, calypsoPo,
                samReader, CalypsoInfo.getSamSettings());

        if(!Arrays.equals(currentPoSN, calypsoPo.getApplicationSerialNumber())) {
            return STATUS_CARD_SWITCHED;
        }

        boolean poProcessStatus = false;
        poProcessStatus = poTransaction.processOpening(
                PoTransaction.ModificationMode.ATOMIC,
                PoTransaction.SessionAccessLevel.SESSION_LVL_LOAD, (byte) 0, (byte) 0);

        if(!poProcessStatus) {
            return STATUS_SESSION_ERROR;
        }

        /* allow to determine the anticipated response */
        poTransaction.prepareReadRecordsCmd(CalypsoInfo.SFI_Counter,
                ReadDataStructure.MULTIPLE_COUNTER, CalypsoInfo.RECORD_NUMBER_1,
                String.format("Counter (SFI=%02X))",
                        CalypsoInfo.SFI_Counter));
        poTransaction.processPoCommands(ChannelState.KEEP_OPEN);

        poTransaction.prepareUpdateRecordCmd(CalypsoInfo.SFI_Contracts, CalypsoInfo.RECORD_NUMBER_1,
                pad("1 MONTH SEASON TICKET", ' ', 29).getBytes(), "Contract: 1 MONTH SEASON TICKET");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd HH:mm:ss");
        String dateTime = LocalDateTime.now().format(formatter);

        String event = pad(dateTime + " OP = +ST", ' ', 29);

        poTransaction.prepareAppendRecordCmd(CalypsoInfo.SFI_EventLog, event.getBytes(), "Event: " + event);

        poProcessStatus = poTransaction.processClosing(TransmissionMode.CONTACTLESS,
                ChannelState.CLOSE_AFTER);

        if(!poProcessStatus) {
            return STATUS_SESSION_ERROR;
        }

        return STATUS_OK;
    }

    /**
     * initial PO content analysis
     * @return
     */
    public boolean analyzePoProfile() {
        boolean status = false;
        if (calypsoPo.isSelected()) {
            currentPoSN = calypsoPo.getApplicationSerialNumber();

            cardContent.setSerialNumber(currentPoSN);

            cardContent.setPoRevision(calypsoPo.getRevision().toString());

            cardContent.setExtraInfo(calypsoPo.getExtraInfo());

            cardContent.setEnvironment(readEnvironmentHolderParser.getRecords());

            cardContent.setEventLog(readEventLogParser.getRecords());

            cardContent.setCounters(readCounterParser.getCounters());

            cardContent.setContracts(readContractParser.getRecords());

            status = true;
        }
        return status;
    }
}
