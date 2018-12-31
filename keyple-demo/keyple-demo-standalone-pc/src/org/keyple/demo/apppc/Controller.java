package org.keyple.demo.apppc;

import ch.qos.logback.classic.LoggerContext;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import org.eclipse.keyple.calypso.command.po.parser.ReadDataStructure;
import org.eclipse.keyple.calypso.command.po.parser.ReadRecordsRespPars;
import org.eclipse.keyple.calypso.transaction.CalypsoPo;
import org.eclipse.keyple.calypso.transaction.PoSelector;
import org.eclipse.keyple.calypso.transaction.PoTransaction;
import org.eclipse.keyple.plugin.pcsc.PcscPlugin;
import org.eclipse.keyple.plugin.pcsc.PcscProtocolSetting;
import org.eclipse.keyple.plugin.pcsc.PcscReader;
import org.eclipse.keyple.seproxy.ChannelState;
import org.eclipse.keyple.seproxy.SeProxyService;
import org.eclipse.keyple.seproxy.SeReader;
import org.eclipse.keyple.seproxy.event.ObservablePlugin;
import org.eclipse.keyple.seproxy.event.ObservablePlugin.PluginObserver;
import org.eclipse.keyple.seproxy.event.ObservableReader;
import org.eclipse.keyple.seproxy.event.ObservableReader.ReaderObserver;
import org.eclipse.keyple.seproxy.event.PluginEvent;
import org.eclipse.keyple.seproxy.event.ReaderEvent;
import org.eclipse.keyple.seproxy.exception.KeypleBaseException;
import org.eclipse.keyple.seproxy.exception.KeyplePluginNotFoundException;
import org.eclipse.keyple.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.seproxy.exception.KeypleReaderNotFoundException;
import org.eclipse.keyple.seproxy.protocol.ContactlessProtocols;
import org.eclipse.keyple.seproxy.protocol.Protocol;
import org.eclipse.keyple.seproxy.protocol.SeProtocolSetting;
import org.eclipse.keyple.seproxy.protocol.TransmissionMode;
import org.eclipse.keyple.transaction.MatchingSe;
import org.eclipse.keyple.transaction.SeSelection;
import org.eclipse.keyple.transaction.SeSelector;
import org.eclipse.keyple.util.ByteArrayUtils;
import org.keyple.demo.apppc.calypso.CalypsoInfo;
import org.keyple.demo.apppc.utils.StaticOutputStreamAppender;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import java.io.OutputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

public class Controller implements Initializable {
    private final static String PREF_PO_READER_FILTER = "PO_READER_FILTER";
    private final static String PREF_SAM_READER_FILTER = "SAM_READER_FILTER";
    private static final String STR_NOT_CONNECTED = "not connected";
    private static final int PANE_WAIT_SYSTEM_READY = 0;
    private static final int PANE_WAIT_CARD_SALES_START = 1;
    private static final int PANE_FILL_CART = 2;
    private static final int PANE_PAYMENT = 3;
    private static final int PANE_LOAD_TITLE = 4;
    private static final int PANE_SALES_RECEIPT = 5;
    public Label lblPo;
    public Label lblError;
    public ComboBox comboLogLevel;
    public ComboBox comboWorkMode;
    public Label lblHolderName;
    private MatchingSe mifareClassic;
    private ReadRecordsRespPars readCounterParser;
    private String choiceStr[] = {"+1 T", "+10 T", "ST 1M"};

    /* application states */
    private enum AppState {
        UNSPECIFIED, WAIT_SYSTEM_READY, WAIT_CARD_SALES_START, FILL_CART, PAYMENT, WAIT_CARD_LOAD_TITLE, LOAD_TITLE,
        SALES_RECEIPT, COMMUNICATION_ERROR, INVALID_PO
    }

    ObservableList<String> logLevelList = FXCollections.observableArrayList("TRACE", "DEBUG", "INFO", "ERROR");
    ObservableList<String> workModeList = FXCollections.observableArrayList("DEMO", "PROFILE1", "PROFILE2", "PROFILE2");

    final Logger logger = (Logger) LoggerFactory.getLogger(Controller.class);
    public Label lblLastTransaction;
    public Label lblConnectReaders;
    public Button btnClearTaLogs;
    public TextArea taLogs;
    public Label lblPOreader;
    public Label lblSAMreader;
    public TextField txtPoReaderFilter;
    public TextField txtSamReaderFilter;
    public Button btnRestart;
    public Accordion accMain;
    Preferences pref;
    SeReader poReader, samReader;
    PoReaderObserver poReaderObserver;
    SamReaderObserver samReaderObserver;
    AppState currentAppState;
    private SeSelection seSelection;
    private ReadRecordsRespPars readEventLogParser;
    private ReadRecordsRespPars readEnvironmentHolderParser;
    private CalypsoPo calypsoPo;
    private byte[] currentPoSN;
    private int choice = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        pref = Preferences.userNodeForPackage(Controller.class);

        /* assign taLogs textArea as output for a logback appender */
        OutputStream os = new TextAreaOutputStream(taLogs);

        StaticOutputStreamAppender.setStaticOutputStream(os);

        /* initialize filters from pref and set listeners */
        txtPoReaderFilter.setText(pref.get(PREF_PO_READER_FILTER, ""));
        txtPoReaderFilter.textProperty().addListener((observable, oldValue, newValue) -> {
            pref.put(PREF_PO_READER_FILTER, newValue);
        });
        txtSamReaderFilter.setText(pref.get(PREF_SAM_READER_FILTER, ""));
        txtSamReaderFilter.textProperty().addListener((observable, oldValue, newValue) -> {
            pref.put(PREF_SAM_READER_FILTER, newValue);
        });

        /* init log level selector */
        comboLogLevel.setItems(logLevelList);
        comboLogLevel.valueProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newLogLevel) {
                logger.info("Log level changed to {}", newLogLevel);
//                logger.setLevel(Level.toLevel(newLogLevel));
                LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
                Logger rootLogger = loggerContext.getLogger("org");
                rootLogger.setLevel(Level.toLevel(newLogLevel));
            }
        });
        comboLogLevel.setValue("INFO");

        /* init work mode */
        comboWorkMode.setItems(workModeList);
        comboWorkMode.setValue("DEMO");

        /* hide "please connect the readers" */
        lblConnectReaders.setVisible(false);
        lblError.setText("");

        setPoReaderName(STR_NOT_CONNECTED);
        setSamReaderName(STR_NOT_CONNECTED);

        /* init accordion */
        for (TitledPane pane : accMain.getPanes()) {
            pane.setCollapsible(false);
        }
        accMain.setExpandedPane(accMain.getPanes().get(0));

        /* applicative state */
        currentAppState = AppState.WAIT_SYSTEM_READY;

        /* launch keyple initialization slightly delayed to let the user see the initialization screen */
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            public void run() {
                keypleInitialization();
            }
        };
        timer.schedule(task, 1000L);
    }

    private void keypleInitialization() {
        lblConnectReaders.setVisible(true);
        logger.info("Instantiate the Keyple SeProxyService.");
        /* Instantiate SeProxyService and add PC/SC plugin */
        SeProxyService seProxyService = SeProxyService.getInstance();

        logger.info("Add the PC/SC plugin.");
        PcscPlugin pcscPlugin = PcscPlugin.getInstance();
        seProxyService.addPlugin(pcscPlugin);

        logger.info("Add an observer to monitor the reader insertion/removal.");
        PcscPluginObserver pluginObserver = new PcscPluginObserver();

        ((ObservablePlugin) pcscPlugin).addObserver(pluginObserver);

        logger.info("Initialization done.");
    }

    public void onClearLogsBtnClicked(MouseEvent mouseEvent) {
        taLogs.clear();
    }

    public void onQuitBtnClicked(MouseEvent mouseEvent) {
        Platform.exit();
        System.exit(0);
    }

    public void onChoice1BtnClicked(MouseEvent mouseEvent) {
        if(currentAppState == AppState.FILL_CART) {
            handleAppEvents(AppState.PAYMENT, null);
            choice = 0;
        }
    }

    public void onChoice2BtnClicked(MouseEvent contextMenuEvent) {
        if(currentAppState == AppState.FILL_CART) {
            handleAppEvents(AppState.PAYMENT, null);
            choice = 1;
        }
    }

    public void onChoice3BtnClicked(MouseEvent contextMenuEvent) {
        if(currentAppState == AppState.FILL_CART) {
            handleAppEvents(AppState.PAYMENT, null);
            choice = 2;
        }
    }

    public void onValidatePaymentImgClicked(MouseEvent mouseEvent) {
        if(currentAppState == AppState.PAYMENT) {
            handleAppEvents(AppState.LOAD_TITLE, null);
            load(choice);
        }
    }

    private void setPoReaderName(String name) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                lblPOreader.setText(name);
            }
        });
    }

    private void setSamReaderName(String name) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                lblSAMreader.setText(name);
            }
        });
    }

    private void activatePane(int idx) {
        logger.info("Request activate pane: {}", idx);
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                for (TitledPane pane : accMain.getPanes()) {
                    pane.setCollapsible(true);
                }
                accMain.setExpandedPane(accMain.getPanes().get(idx));
                for (TitledPane pane : accMain.getPanes()) {
                    pane.setCollapsible(false);
                }
                logger.info("Activate pane: {}", idx);
            }
        });
    }

    private void setLabel(Label label, String text) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                label.setText(text);
            }
        });
    }

    private void handleAppEvents(AppState appState, ReaderEvent readerEvent) {
        logger.info("Current state = {}, wanted new state = {}, event = {}", currentAppState, appState,
                readerEvent == null ? "null" : readerEvent.getEventType());
        if (readerEvent != null && readerEvent.getEventType().equals(ReaderEvent.EventType.SE_INSERTED)) {
            if (!seSelection.processDefaultSelection(readerEvent.getDefaultSelectionResponse())) {
                logger.error("PO Not selected");
            }
        }
        switch (appState) {
            case WAIT_SYSTEM_READY:
                activatePane(PANE_WAIT_SYSTEM_READY);
                currentAppState = appState;
                break;
            case WAIT_CARD_SALES_START:
                if (currentAppState != appState) {
                    activatePane(PANE_WAIT_CARD_SALES_START);
                    currentAppState = AppState.WAIT_CARD_SALES_START;
                }
                if (readerEvent != null && readerEvent.getEventType().equals(ReaderEvent.EventType.SE_INSERTED)) {
                    if (comboWorkMode.getValue().equals("DEMO")) {
                        if (analyzePoProfile()) {
                            activatePane(PANE_FILL_CART);
                            currentAppState = AppState.FILL_CART;
                        }
                    } else {
                        if (personalize(comboWorkMode.getValue().toString())) {
                            setLabel(lblError, "The PO has been personalized!");
                            Timer timer = new Timer();
                            TimerTask task = new TimerTask() {
                                public void run() {
                                    handleAppEvents(AppState.WAIT_CARD_SALES_START, null);
                                    setLabel(lblError, "");
                                }
                            };
                            timer.schedule(task, 5000L);
                            activatePane(PANE_WAIT_CARD_SALES_START);
                            currentAppState = AppState.WAIT_CARD_SALES_START;
                        } else {
                            setLabel(lblError, "The personalization failed!");
                            Timer timer = new Timer();
                            TimerTask task = new TimerTask() {
                                public void run() {
                                    handleAppEvents(AppState.WAIT_CARD_SALES_START, null);
                                    setLabel(lblError, "");
                                }
                            };
                            timer.schedule(task, 5000L);
                        }
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                comboWorkMode.setValue("DEMO");
                            }
                        });
                    }
                }
                break;
            case FILL_CART:
                if (readerEvent != null && readerEvent.getEventType() == ReaderEvent.EventType.SE_INSERTED) {
                    if (Arrays.equals(calypsoPo.getApplicationSerialNumber(), currentPoSN)) {
                        setLabel(lblPo, "S/N " + ByteArrayUtils.toHex(currentPoSN) + ", " + calypsoPo.getRevision());
                    } else {
                        setLabel(lblError, "The PO has been switched!");
                        Timer timer = new Timer();
                        TimerTask task = new TimerTask() {
                            public void run() {
                                handleAppEvents(AppState.WAIT_CARD_SALES_START, null);
                                setLabel(lblError, "");
                            }
                        };
                        timer.schedule(task, 5000L);
                    }
                }
                break;
            case PAYMENT:
                activatePane(PANE_PAYMENT);
                currentAppState = appState;
                break;
            case WAIT_CARD_LOAD_TITLE:
                activatePane(PANE_LOAD_TITLE);
                currentAppState = appState;
                break;
            case LOAD_TITLE:
                activatePane(PANE_LOAD_TITLE);
                currentAppState = appState;
                break;
            case SALES_RECEIPT:
                activatePane(PANE_SALES_RECEIPT);
                currentAppState = appState;
                break;
            case COMMUNICATION_ERROR:
                currentAppState = appState;
                break;
            case INVALID_PO:
                currentAppState = appState;
                break;
        }
        if (readerEvent != null) {
            if (!readerEvent.getEventType().equals(ReaderEvent.EventType.SE_INSERTED)) {
                setLabel(lblPo, "-");
            }
        }
        logger.info("New state = {}", currentAppState);
    }

    private boolean personalize(String profile) {
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

        if("PROFILE1".equals(profile)) {
            poTransaction.prepareUpdateRecordCmd(CalypsoInfo.SFI_EnvironmentAndHolder, CalypsoInfo.RECORD_NUMBER_1,
                    "John Smith".getBytes(), "HolderName: John Smith");
        } else {
            poTransaction.prepareUpdateRecordCmd(CalypsoInfo.SFI_EnvironmentAndHolder, CalypsoInfo.RECORD_NUMBER_1,
                    "Harry Potter".getBytes(), "HolderName: Harry Potter");
        }

        poTransaction.prepareUpdateRecordCmd(CalypsoInfo.SFI_EventLog, CalypsoInfo.RECORD_NUMBER_1,
                ByteArrayUtils.fromHex("000000000000000000000000000000000000000000000000000000"),
                "Event: blank");

//        poTransaction.prepareUpdateRecordCmd(CalypsoInfo.SFI_Counter, CalypsoInfo.RECORD_NUMBER_1, ByteArrayUtils
// .fromHex("0003E8"),
//                "Counter: 1000");

        try {
            poProcessStatus = poTransaction.processClosing(TransmissionMode.CONTACTLESS,
                    ChannelState.CLOSE_AFTER);
        } catch (KeypleReaderException e) {
            e.printStackTrace();
        }
        calypsoPo.reset();

        return poProcessStatus;
    }

    private boolean load(int choice) {
        PoTransaction poTransaction = new PoTransaction(poReader, calypsoPo,
                samReader, CalypsoInfo.getSamSettings());

        boolean poProcessStatus = false;
        try {
            poProcessStatus = poTransaction.processOpening(
                    PoTransaction.ModificationMode.ATOMIC,
                    PoTransaction.SessionAccessLevel.SESSION_LVL_DEBIT, (byte) 0, (byte) 0);
        } catch (KeypleReaderException e) {
            e.printStackTrace();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String dateTime = LocalDateTime.now().format(formatter);

        String event = choiceStr[choice] + "|" + dateTime;

        poTransaction.prepareAppendRecordCmd(CalypsoInfo.SFI_EventLog, event.getBytes(),"Event: " + event);

        try {
            poProcessStatus = poTransaction.processClosing(TransmissionMode.CONTACTLESS,
                    ChannelState.CLOSE_AFTER);
        } catch (KeypleReaderException e) {
            e.printStackTrace();
        }
        calypsoPo.reset();
        return true;
    }

    private boolean analyzePoProfile() {
        boolean status = false;
        if (calypsoPo.isSelected()) {
            currentPoSN = calypsoPo.getApplicationSerialNumber();
            setLabel(lblPo, "S/N " + ByteArrayUtils.toHex(currentPoSN) + ", " + calypsoPo.getRevision());

            SortedMap<Integer, byte[]> records = readEventLogParser.getRecords();
            String log = "";
            for(int i=0; i<records.size(); i++) {
                String rec = new String(records.get(i+1));
                log += "[" + i + "] " + rec;
                if(i<(records.size()-1)) {
                    log += " ;     ";
                }
            }

            logger.info("Last transaction: {}", log);

            setLabel(lblLastTransaction, log);

            String holderName =
                    new String(readEnvironmentHolderParser.getRecords().get((int) CalypsoInfo.RECORD_NUMBER_1));

            logger.info("Holder name: {}", holderName);

            setLabel(lblHolderName, holderName);

            /* TODO https://github.com/calypsonet/keyple-java/issues/453 */
            status = true;
        } else {
            if (mifareClassic.getSelectionSeResponse() != null
                    && mifareClassic.getSelectionSeResponse().getSelectionStatus() != null
                    && mifareClassic.getSelectionSeResponse().getSelectionStatus().hasMatched()) {
            }
        }
        return status;
    }

    private void initializePoReader(SeReader reader) throws KeypleBaseException {
        if (reader != null) {
            poReader = reader;
            setPoReaderName(poReader.getName());
            if (poReaderObserver == null) {
                poReaderObserver = new PoReaderObserver();
            }
            ((ObservableReader) poReader).addObserver(poReaderObserver);
            poReader.setParameter(PcscReader.SETTING_KEY_LOGGING, "true");
            poReader.setParameter(PcscReader.SETTING_KEY_PROTOCOL, PcscReader.SETTING_PROTOCOL_T1);
            /* Set the PO reader protocol flag for ISO14443 */
            reader.addSeProtocolSetting(
                    new SeProtocolSetting(PcscProtocolSetting.SETTING_PROTOCOL_ISO14443_4));
            /* Set the PO reader protocol flag for Mifare Classic */
            reader.addSeProtocolSetting(
                    new SeProtocolSetting(PcscProtocolSetting.SETTING_PROTOCOL_MIFARE_CLASSIC));
            /* change appState if both readers are ready */
            if (samReader != null) {
                handleAppEvents(AppState.WAIT_CARD_SALES_START, null);
            }
            /* init default selection */
            prepareAndSetPoDefaultSelection();
        } else {
            throw new IllegalStateException("reader is null");
        }
    }

    private void deInitializePoReader(SeReader reader) {
        if (reader != null) {
            if (poReader != null) {
                setPoReaderName(STR_NOT_CONNECTED);
                ((ObservableReader) poReader).removeObserver(poReaderObserver);
                handleAppEvents(AppState.WAIT_SYSTEM_READY, null);
                poReader = null;
            }
        } else {
            throw new IllegalStateException("reader is null");
        }
    }

    private void initializeSamReader(SeReader reader) throws KeypleBaseException {
        if (reader != null) {
            samReader = reader;
            setSamReaderName(samReader.getName());
            if (samReaderObserver == null) {
                samReaderObserver = new SamReaderObserver();
            }
            ((ObservableReader) samReader).addObserver(samReaderObserver);
            samReader.setParameter(PcscReader.SETTING_KEY_LOGGING, "true");
            samReader.setParameter(PcscReader.SETTING_KEY_PROTOCOL, PcscReader.SETTING_PROTOCOL_T0);
            /* change appState if both readers are ready */
            if (poReader != null) {
                handleAppEvents(AppState.WAIT_CARD_SALES_START, null);
            }
        } else {
            throw new IllegalStateException("reader is null");
        }
    }

    private void deInitializeSamReader(SeReader reader) {
        if (reader != null) {
            if (samReader != null) {
                setSamReaderName(STR_NOT_CONNECTED);
                ((ObservableReader) samReader).removeObserver(samReaderObserver);
                handleAppEvents(AppState.WAIT_SYSTEM_READY, null);
                samReader = null;
            }
        } else {
            throw new IllegalStateException("reader is null");
        }
    }

    private void assignReaderRole(SeReader reader) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Reader association");
                alert.setHeaderText("The reader '" + reader.getName() + "' is not associated.");
                alert.setContentText("Select the usage for this reader:");

                ButtonType buttonTypePo = new ButtonType("PO");
                ButtonType buttonTypeSam = new ButtonType("SAM");
                ButtonType buttonTypeCancel = new ButtonType("Ignore", ButtonBar.ButtonData.CANCEL_CLOSE);

                alert.getButtonTypes().setAll(buttonTypePo, buttonTypeSam, buttonTypeCancel);

                Optional<ButtonType> result = alert.showAndWait();
                try {
                    if (result.get() == buttonTypePo) {
                        initializePoReader(reader);
                        txtPoReaderFilter.setText(poReader.getName());
                    } else if (result.get() == buttonTypeSam) {
                        initializeSamReader(reader);
                        txtSamReaderFilter.setText(samReader.getName());
                    } else {
                        // ... user chose CANCEL or closed the dialog
                    }
                } catch (KeypleBaseException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void checkSamAndOpenChannel(SeReader samReader) {
        /*
         * check the availability of the SAM doing a ATR based selection, open its physical and
         * logical channels and keep it open
         */
        SeSelection samSelection = new SeSelection(samReader);

        SeSelector samSelector = new SeSelector(CalypsoInfo.SAM_C1_ATR_REGEX,
                ChannelState.KEEP_OPEN, Protocol.ANY, "Selection SAM C1");

        /* Prepare selector, ignore MatchingSe here */
        samSelection.prepareSelection(samSelector);

        try {
            if (!samSelection.processExplicitSelection()) {
                throw new IllegalStateException("Unable to open a logical channel for SAM!");
            } else {
            }
        } catch (KeypleReaderException e) {
            throw new IllegalStateException("Reader exception: " + e.getMessage());

        }
    }

    public void onRestartBtnClicked(MouseEvent mouseEvent) {
        handleAppEvents(AppState.WAIT_CARD_SALES_START, null);
    }

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

        readEventLogParser = calypsoPoSelector.prepareReadRecordsCmd(CalypsoInfo.SFI_EventLog,
                ReadDataStructure.MULTIPLE_RECORD_DATA, CalypsoInfo.RECORD_NUMBER_1,
                String.format("EventLog (SFI=%02X))",
                        CalypsoInfo.SFI_EventLog));

        readCounterParser = calypsoPoSelector.prepareReadRecordsCmd(CalypsoInfo.SFI_Counter,
                ReadDataStructure.SINGLE_COUNTER, CalypsoInfo.RECORD_NUMBER_1,
                String.format("Counter (SFI=%02X))",
                        CalypsoInfo.SFI_Counter));

        /*
         * Add the selection case to the current selection (we could have added other cases here)
         */
        calypsoPo = (CalypsoPo) seSelection.prepareSelection(calypsoPoSelector);

        /* Select Mifare Classic PO */
        PoSelector mifareClassicSelector = new PoSelector(".*", ChannelState.CLOSE_AFTER,
                ContactlessProtocols.PROTOCOL_MIFARE_CLASSIC, "Mifare classic");

        /*
         * Add the selection case to the current selection (we could have added other cases here)
         */
        mifareClassic = seSelection.prepareSelection(mifareClassicSelector);

        /*
         * Provide the SeReader with the selection operation to be processed when a PO is inserted.
         */
        ((ObservableReader) poReader).setDefaultSelectionRequest(
                seSelection.getSelectionOperation(),
                ObservableReader.NotificationMode.ALWAYS);
    }

    private static class TextAreaOutputStream extends OutputStream {

        private final TextArea textArea;

        TextAreaOutputStream(TextArea textArea) {
            this.textArea = textArea;
        }

        StringBuilder stb = new StringBuilder();

        @Override
        public void write(int b) {
            stb.append((char) b);

            if (b == '\n') {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        textArea.appendText(stb.toString());
                        stb.setLength(0);
                    }
                });
            }
        }
    }

    private class PcscPluginObserver implements PluginObserver {

        @Override
        public void update(PluginEvent pluginEvent) {
            SeReader reader = null;
            logger.info("PluginEvent: PLUGINNAME = {}, READERNAME = {}, EVENTTYPE = {}",
                    pluginEvent.getPluginName(), pluginEvent.getReaderName(), pluginEvent.getEventType());

            try {
                reader = SeProxyService.getInstance().getPlugin(pluginEvent.getPluginName())
                        .getReader(pluginEvent.getReaderName());
            } catch (KeyplePluginNotFoundException e) {
                e.printStackTrace();
            } catch (KeypleReaderNotFoundException e) {
                e.printStackTrace();
            }
            switch (pluginEvent.getEventType()) {
                case READER_CONNECTED:
                    logger.info("New reader! READERNAME = {}", reader.getName());
                    try {
                        String filter = txtPoReaderFilter.getText();
                        Pattern p = Pattern.compile(".*" + filter + ".*");
                        if (filter.length() > 0 && p.matcher(reader.getName()).matches()) {
                            initializePoReader(reader);
                        } else {
                            filter = txtSamReaderFilter.getText();
                            p = Pattern.compile(".*" + filter + ".*");
                            if (filter.length() > 0 && p.matcher(reader.getName()).matches()) {
                                initializeSamReader(reader);
                            } else {
                                assignReaderRole(reader);
                            }
                        }
                    } catch (KeypleBaseException e) {
                        e.printStackTrace();
                    }
                    break;

                case READER_DISCONNECTED:
                    logger.info("Reader removed. READERNAME = {}", pluginEvent.getReaderName());
                    if (poReader != null && reader != null && poReader.getName().equals(reader.getName())) {
                        deInitializePoReader(reader);
                    } else {
                        if (samReader != null && reader != null && samReader.getName().equals(reader.getName())) {
                            deInitializeSamReader(reader);
                        }
                    }
                    break;

                default:
                    logger.info("Unexpected reader event. EVENT = {}",
                            pluginEvent.getEventType().getName());
                    break;
            }
        }
    }

    private class PoReaderObserver implements ReaderObserver {

        @Override
        public void update(ReaderEvent readerEvent) {
            logger.info("PO Reader event = {}", readerEvent.getEventType());
            handleAppEvents(currentAppState, readerEvent);
        }
    }

    private class SamReaderObserver implements ReaderObserver {

        @Override
        public void update(ReaderEvent readerEvent) {
            logger.info("SAM Reader event = {}", readerEvent.getEventType());
            if (readerEvent.getEventType() == ReaderEvent.EventType.SE_INSERTED) {
                /* SAM open logical channel */
                checkSamAndOpenChannel(samReader);
            }
        }
    }
}
