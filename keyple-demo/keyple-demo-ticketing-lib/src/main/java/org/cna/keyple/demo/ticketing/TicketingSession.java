package org.cna.keyple.demo.ticketing;


import org.eclipse.keyple.calypso.command.po.parser.ReadDataStructure;
import org.eclipse.keyple.calypso.command.po.parser.ReadRecordsRespPars;
import org.eclipse.keyple.calypso.transaction.*;
import org.eclipse.keyple.calypso.transaction.exception.KeypleCalypsoTransactionSerialNumberNotMatching;
import org.eclipse.keyple.core.selection.AbstractMatchingSe;
import org.eclipse.keyple.core.selection.AbstractSeSelectionRequest;
import org.eclipse.keyple.core.selection.SeSelection;
import org.eclipse.keyple.core.selection.SelectionsResult;
import org.eclipse.keyple.core.seproxy.ChannelState;
import org.eclipse.keyple.core.seproxy.SeReader;
import org.eclipse.keyple.core.seproxy.SeSelector;
import org.eclipse.keyple.core.seproxy.event.AbstractDefaultSelectionsResponse;
import org.eclipse.keyple.core.seproxy.event.ObservableReader;
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.core.seproxy.message.SeResponse;
import org.eclipse.keyple.core.seproxy.protocol.SeCommonProtocols;
import org.eclipse.keyple.core.seproxy.protocol.TransmissionMode;
import org.eclipse.keyple.core.util.ByteArrayUtil;
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
    private AbstractMatchingSe mifareClassic, mifareDesfire, bankingCard;
    private CalypsoPo calypsoPo;
    private SeSelection seSelection;
    private int readEventLogParserIndex, readEnvironmentHolderParserIndex, readCounterParserIndex,
            readContractParserIndex;
    private String poTypeName;
    private CardContent cardContent;
    private final SeReader poReader;
    private final SamResource samResource;
    private byte[] currentPoSN;
    final Logger logger = (Logger) LoggerFactory.getLogger(TicketingSession.class);
    private int calypsoPoIndex;
    private int mifareClassicIndex;
    private int mifareDesfireIndex;
    private int bankingCardIndex;
    private int navigoCardIndex;
    private ReadRecordsRespPars readEnvironmentHolderParser;
    private ReadRecordsRespPars readEventLogParser;
    private ReadRecordsRespPars readCounterParser;
    private ReadRecordsRespPars readContractParser;

    private String pad(String text, char c, int length) {
        StringBuffer sb = new StringBuffer(length);
        sb.append(text);
        for (int i = text.length(); i < length; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    public TicketingSession(SeReader poReader, SamResource samResource) {
        this.poReader = poReader;
        this.samResource = samResource;
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
        seSelection = new SeSelection();

        /* Select Calypso */
        PoSelectionRequest poSelectionRequest = new PoSelectionRequest(new PoSelector(
                SeCommonProtocols.PROTOCOL_ISO14443_4, null,
                new PoSelector.PoAidSelector(new SeSelector.AidSelector.IsoAid(CalypsoInfo.AID),
                        PoSelector.InvalidatedPo.REJECT),
                "AID: " + CalypsoInfo.AID), ChannelState.KEEP_OPEN);

        readEnvironmentHolderParserIndex =
                poSelectionRequest.prepareReadRecordsCmd(CalypsoInfo.SFI_EnvironmentAndHolder,
                ReadDataStructure.SINGLE_RECORD_DATA, CalypsoInfo.RECORD_NUMBER_1,
                String.format("EnvironmentHolder (SFI=%02X))",
                        CalypsoInfo.SFI_EnvironmentAndHolder));

        readContractParserIndex = poSelectionRequest.prepareReadRecordsCmd(CalypsoInfo.SFI_Contracts,
                ReadDataStructure.SINGLE_RECORD_DATA, CalypsoInfo.RECORD_NUMBER_1,
                String.format("Contracts#1 (SFI=%02X))",
                        CalypsoInfo.SFI_Contracts));

        readCounterParserIndex = poSelectionRequest.prepareReadRecordsCmd(CalypsoInfo.SFI_Counter,
                ReadDataStructure.MULTIPLE_COUNTER, CalypsoInfo.RECORD_NUMBER_1,
                String.format("Counter (SFI=%02X))",
                        CalypsoInfo.SFI_Counter));

        readEventLogParserIndex = poSelectionRequest.prepareReadRecordsCmd(CalypsoInfo.SFI_EventLog,
                ReadDataStructure.MULTIPLE_RECORD_DATA, CalypsoInfo.RECORD_NUMBER_1,
                String.format("EventLog (SFI=%02X))",
                        CalypsoInfo.SFI_EventLog));

        /*
         * Add the selection case to the current selection (we could have added other cases here)
         */
        calypsoPoIndex = seSelection.prepareSelection(poSelectionRequest);

        /* Select Mifare Classic PO */
        GenericSeSelectionRequest mifareClassicSelectionRequest =
                new GenericSeSelectionRequest(new SeSelector(SeCommonProtocols.PROTOCOL_MIFARE_CLASSIC,
                        new SeSelector.AtrFilter(".*"), null, "Mifare classic"), ChannelState.CLOSE_AFTER);

        /*
         * Add the selection case to the current selection
         */
        mifareClassicIndex = seSelection.prepareSelection(mifareClassicSelectionRequest);

        /* Select Mifare Desfire PO */
        GenericSeSelectionRequest mifareDesfireSelectionRequest =
                new GenericSeSelectionRequest(new SeSelector(SeCommonProtocols.PROTOCOL_MIFARE_DESFIRE,
                        new SeSelector.AtrFilter(".*"), null, "Mifare desfire"), ChannelState.CLOSE_AFTER);

        /*
         * Add the selection case to the current selection
         */
        mifareDesfireIndex = seSelection.prepareSelection(mifareDesfireSelectionRequest);

        GenericSeSelectionRequest bankingCardSelectionRequest =
                new GenericSeSelectionRequest(new SeSelector(SeCommonProtocols.PROTOCOL_ISO14443_4,
                        null, new SeSelector.AidSelector(new SeSelector.AidSelector.IsoAid(
                        "325041592e5359532e4444463031"),
                        null, SeSelector.AidSelector.FileOccurrence.FIRST,
                        SeSelector.AidSelector.FileControlInformation.FCI), "EMV"), ChannelState.CLOSE_AFTER);

        /*
         * Add the selection case to the current selection
         */
        bankingCardIndex = seSelection.prepareSelection(bankingCardSelectionRequest);

        GenericSeSelectionRequest naviogCardSelectionRequest =
                new GenericSeSelectionRequest(new SeSelector(SeCommonProtocols.PROTOCOL_ISO14443_4,
                        null, new SeSelector.AidSelector(new SeSelector.AidSelector.IsoAid("A0000004040125090101"),
                        null, SeSelector.AidSelector.FileOccurrence.FIRST,
                        SeSelector.AidSelector.FileControlInformation.FCI), "EMV"), ChannelState.CLOSE_AFTER);

        /*
         * Add the selection case to the current selection
         */
        navigoCardIndex = seSelection.prepareSelection(naviogCardSelectionRequest);

        /*
         * Provide the SeReader with the selection operation to be processed when a PO is inserted.
         */
        ((ObservableReader) poReader).setDefaultSelectionRequest(
                seSelection.getSelectionOperation(),
                ObservableReader.NotificationMode.ALWAYS);
    }

    public SelectionsResult processDefaultSelection(AbstractDefaultSelectionsResponse selectionResponse) {
        SelectionsResult selectionsResult;
        logger.info("selectionResponse");
        logger.info("selectionResponse = {}", selectionResponse);
        selectionsResult = seSelection.processDefaultSelection(selectionResponse);

        if (selectionsResult.hasActiveSelection()) {
            int selectionIndex = selectionsResult.getActiveSelection().getSelectionIndex();
            if (selectionIndex == calypsoPoIndex) {
                calypsoPo = (CalypsoPo)selectionsResult.getActiveSelection().getMatchingSe();
                poTypeName = "CALYPSO";
                readEnvironmentHolderParser =
                        (ReadRecordsRespPars) selectionsResult.getActiveSelection().getResponseParser(readEnvironmentHolderParserIndex);
                readEventLogParser =
                        (ReadRecordsRespPars) selectionsResult.getActiveSelection().getResponseParser(readEventLogParserIndex);
                readCounterParser =
                        (ReadRecordsRespPars) selectionsResult.getActiveSelection().getResponseParser(readCounterParserIndex);
                readContractParser =
                        (ReadRecordsRespPars) selectionsResult.getActiveSelection().getResponseParser(readContractParserIndex);
            } else if (selectionIndex == mifareClassicIndex) {
                poTypeName = "MIFARE Classic";
            } else if (selectionIndex == mifareDesfireIndex) {
                poTypeName = "MIFARE Desfire";
            } else if (selectionIndex == bankingCardIndex) {
                poTypeName = "EMV";
            } else if (selectionIndex == navigoCardIndex) {
                poTypeName = "NAVIGO";
            } else {
                poTypeName = "OTHER";
            }
        }
        logger.info("PO type = {}", poTypeName);
        return selectionsResult;
    }

    public String getPoTypeName() {
        return poTypeName;
    }

    public CardContent getCardContent() {
        return cardContent;
    }

    public String getPoIdentification() {
        return ByteArrayUtil.toHex(calypsoPo.getApplicationSerialNumber()) + ", " + calypsoPo.getRevision().toString();
    }

    /**
     * do the personalization of the PO according to the specified profile
     *
     * @param profile
     * @return
     */
    public boolean personalize(String profile) throws KeypleCalypsoTransactionSerialNumberNotMatching {
        PoTransaction poTransaction = new PoTransaction(new PoResource(poReader, calypsoPo),
                new TransactionSettings(samResource));

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

        poTransaction.prepareUpdateRecordCmd(CalypsoInfo.SFI_Counter, CalypsoInfo.RECORD_NUMBER_1, ByteArrayUtil
                .fromHex(pad("", '0', 29 * 2)), "Reset all counters");

        try {
            poProcessStatus = poTransaction.processClosing(ChannelState.CLOSE_AFTER);
        } catch (KeypleReaderException e) {
            e.printStackTrace();
        }

        return poProcessStatus;
    }

    /**
     * load the PO according to the choice provided as an argument
     *
     * @param ticketNumber
     * @return
     * @throws KeypleReaderException
     */
    public int loadTickets(int ticketNumber) throws KeypleReaderException {
        PoTransaction poTransaction = new PoTransaction(new PoResource(poReader, calypsoPo),
                new TransactionSettings(samResource));

        if (!Arrays.equals(currentPoSN, calypsoPo.getApplicationSerialNumber())) {
            return STATUS_CARD_SWITCHED;
        }

        boolean poProcessStatus = false;
        poProcessStatus = poTransaction.processOpening(
                PoTransaction.ModificationMode.ATOMIC,
                PoTransaction.SessionAccessLevel.SESSION_LVL_LOAD, (byte) 0, (byte) 0);

        if (!poProcessStatus) {
            return STATUS_SESSION_ERROR;
        }

        /* allow to determine the anticipated response */
        poTransaction.prepareReadRecordsCmd(CalypsoInfo.SFI_Counter,
                ReadDataStructure.MULTIPLE_COUNTER, CalypsoInfo.RECORD_NUMBER_1,
                String.format("Counter (SFI=%02X))",
                        CalypsoInfo.SFI_Counter));
        poTransaction.processPoCommandsInSession();
        poTransaction.prepareIncreaseCmd(CalypsoInfo.SFI_Counter, (byte) 0x01, ticketNumber,
                "Increase " + ticketNumber);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd HH:mm:ss");
        String dateTime = LocalDateTime.now().format(formatter);

        String event = "";
        if (ticketNumber > 0) {
            event = pad(dateTime + " OP = +" + ticketNumber, ' ', 29);
        } else {
            event = pad(dateTime + " T1", ' ', 29);
        }

        poTransaction.prepareAppendRecordCmd(CalypsoInfo.SFI_EventLog, event.getBytes(), "Event: " + event);

        poProcessStatus = poTransaction.processClosing(ChannelState.CLOSE_AFTER);

        if (!poProcessStatus) {
            return STATUS_SESSION_ERROR;
        }

        return STATUS_OK;
    }


    /**
     * Load a season ticket contract
     *
     * @return
     * @throws KeypleReaderException
     */
    public int loadContract() throws KeypleReaderException {
        PoTransaction poTransaction = new PoTransaction(new PoResource(poReader, calypsoPo),
                new TransactionSettings(samResource));

        if (!Arrays.equals(currentPoSN, calypsoPo.getApplicationSerialNumber())) {
            return STATUS_CARD_SWITCHED;
        }

        boolean poProcessStatus = false;
        poProcessStatus = poTransaction.processOpening(
                PoTransaction.ModificationMode.ATOMIC,
                PoTransaction.SessionAccessLevel.SESSION_LVL_LOAD, (byte) 0, (byte) 0);

        if (!poProcessStatus) {
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

        poProcessStatus = poTransaction.processClosing(ChannelState.CLOSE_AFTER);

        if (!poProcessStatus) {
            return STATUS_SESSION_ERROR;
        }

        return STATUS_OK;
    }

    /**
     * initial PO content analysis
     *
     * @return
     */
    public boolean analyzePoProfile() {
        boolean status = false;
        if (calypsoPo.isSelected()) {
            currentPoSN = calypsoPo.getApplicationSerialNumber();

            cardContent.setSerialNumber(currentPoSN);

            cardContent.setPoRevision(calypsoPo.getRevision().toString());

            cardContent.setExtraInfo(calypsoPo.getSelectionExtraInfo());

            cardContent.setEnvironment(readEnvironmentHolderParser.getRecords());

            cardContent.setEventLog(readEventLogParser.getRecords());

            cardContent.setCounters(readCounterParser.getCounters());

            cardContent.setContracts(readContractParser.getRecords());

            status = true;
        }
        return status;
    }

    @Override
    public String toString() {
        return "poReader:" + poReader.getName() + "- samReader:" + samResource.getSeReader().getName()
                + " - cardContent:"
                + this.getCardContent() != null ? cardContent.toString() : "null"
                + " - PoTypeName:"
                + this.getPoTypeName() != null ? cardContent.getPoTypeName() : "null";
    }

    /**
     * Create a new class extending AbstractSeSelectionRequest
     */
    public class GenericSeSelectionRequest extends AbstractSeSelectionRequest {
        TransmissionMode transmissionMode;

        public GenericSeSelectionRequest(SeSelector seSelector, ChannelState channelState) {
            super(seSelector, channelState);
            transmissionMode = seSelector.getSeProtocol().getTransmissionMode();
        }

        @Override
        protected AbstractMatchingSe parse(SeResponse seResponse) {
            class GenericMatchingSe extends AbstractMatchingSe {
                public GenericMatchingSe(SeResponse selectionResponse,
                                         TransmissionMode transmissionMode, String extraInfo) {
                    super(selectionResponse, transmissionMode, extraInfo);
                }
            }
            return new GenericMatchingSe(seResponse, transmissionMode, "Generic Matching SE");
        }
    }

}
