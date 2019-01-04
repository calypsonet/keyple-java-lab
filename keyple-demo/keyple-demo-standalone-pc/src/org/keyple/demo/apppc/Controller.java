package org.keyple.demo.apppc;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import org.eclipse.keyple.calypso.command.po.parser.ReadRecordsRespPars;
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
import org.eclipse.keyple.seproxy.exception.*;
import org.eclipse.keyple.seproxy.protocol.Protocol;
import org.eclipse.keyple.seproxy.protocol.SeProtocolSetting;
import org.eclipse.keyple.transaction.SeSelection;
import org.eclipse.keyple.transaction.SeSelector;
import org.keyple.demo.apppc.calypso.CardContent;
import org.keyple.demo.apppc.calypso.CalypsoInfo;
import org.keyple.demo.apppc.utils.StaticOutputStreamAppender;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;
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
    public Label lblMessage;
    public ComboBox comboLogLevel;
    public ComboBox comboWorkMode;
    public Label lblHolderName;
    public Label lblCounters;
    public Label lblAmount;
    public Label lblSaleItem;
    public Label lblSaleDate;
    public Label lblSaleAmount;
    public Label lblContracts;
    private TicketingSessionManager ticketingSessionManager;
    private TicketingSession ticketingSession;


    /* application states */
    private enum AppState {
        UNSPECIFIED, WAIT_SYSTEM_READY, WAIT_CARD_SALES_START, FILL_CART, PAYMENT, WAIT_CARD_LOAD_TITLE, LOAD_TITLE,
        SALES_RECEIPT, COMMUNICATION_ERROR, INVALID_PO
    }

    ObservableList<String> logLevelList = FXCollections.observableArrayList("TRACE", "DEBUG", "INFO", "ERROR");
    ObservableList<String> workModeList = FXCollections.observableArrayList("DEMO", "PROFILE1", "PROFILE2");

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
    SeReader samReader;
    String poReaderName = "";
    PoReaderObserver poReaderObserver;
    SamReaderObserver samReaderObserver;
    AppState currentAppState;
    private int choice = 0;

    /**
     * initialize the view
     *
     * @param location
     * @param resources
     */
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
                LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
                Logger rootLogger = loggerContext.getLogger("org");
                rootLogger.setLevel(Level.toLevel(newLogLevel));
            }
        });
        comboLogLevel.setValue("TRACE");

        /* init work mode */
        comboWorkMode.setItems(workModeList);
        comboWorkMode.setValue("DEMO");

        /* hide "please connect the readers" */
        lblConnectReaders.setVisible(false);
        lblMessage.setText("");

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

    /**
     * keyple readers initialization
     */
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

    /**
     * clear the log area
     *
     * @param mouseEvent
     */
    public void onClearLogsBtnClicked(MouseEvent mouseEvent) {
        taLogs.clear();
    }

    /**
     * exit the application
     *
     * @param mouseEvent
     */
    public void onQuitBtnClicked(MouseEvent mouseEvent) {
        Platform.exit();
        System.exit(0);
    }

    /**
     * handle the choice 2 button event
     *
     * @param mouseEvent
     * @throws NoStackTraceThrowable
     */
    public void onChoice1BtnClicked(MouseEvent mouseEvent) throws NoStackTraceThrowable, KeypleReaderNotFoundException {
        if (currentAppState == AppState.FILL_CART) {
            if (ticketingSession.getPoReader().isSePresent()) {
                choice = 0;
                lblAmount.setText("2 €");
                lblSaleItem.setText("One-way ticket");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String dateTime = LocalDateTime.now().format(formatter);
                lblSaleDate.setText(dateTime);
                lblSaleAmount.setText("2.00 €");
                handleAppEvents(AppState.PAYMENT, null);
            } else {
                displayMessage("Put the card back on the reader", 0);
            }
        }
    }

    /**
     * handle the choice 2 button event
     *
     * @param mouseEvent
     * @throws NoStackTraceThrowable
     */
    public void onChoice2BtnClicked(MouseEvent mouseEvent) throws NoStackTraceThrowable, KeypleReaderNotFoundException {
        if (currentAppState == AppState.FILL_CART) {
            if (ticketingSession.getPoReader().isSePresent()) {
                choice = 1;
                lblAmount.setText("18 €");
                lblSaleItem.setText("10 tickets booklet");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String dateTime = LocalDateTime.now().format(formatter);
                lblSaleDate.setText(dateTime);
                lblSaleAmount.setText("18.00 €");
                handleAppEvents(AppState.PAYMENT, null);
            } else {
                displayMessage("Put the card back on the reader", 0);
            }
        }
    }

    /**
     * handle the choice 3 button event
     *
     * @param mouseEvent
     * @throws NoStackTraceThrowable
     */
    public void onChoice3BtnClicked(MouseEvent mouseEvent) throws NoStackTraceThrowable, KeypleReaderNotFoundException {
        if (currentAppState == AppState.FILL_CART) {
            if (ticketingSession.getPoReader().isSePresent()) {
                choice = 2;
                lblAmount.setText("50 €");
                lblSaleItem.setText("1 month season ticket");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String dateTime = LocalDateTime.now().format(formatter);
                lblSaleDate.setText(dateTime);
                lblSaleAmount.setText("50.00 €");
                handleAppEvents(AppState.PAYMENT, null);
            } else {
                displayMessage("Put the card back on the reader", 0);
            }
        }
    }

    /**
     * handle the payment validation event
     *
     * @param mouseEvent
     * @throws NoStackTraceThrowable, KeypleReaderException
     */
    public void onValidatePaymentImgClicked(MouseEvent mouseEvent) {
        if (currentAppState == AppState.PAYMENT) {
            try {
                if (ticketingSession.getPoReader().isSePresent()) {
                    int loadStatus = load(choice);
                    switch (loadStatus) {
                        case TicketingSession.STATUS_OK:
                            handleAppEvents(AppState.LOAD_TITLE, null);
                            break;
                        case TicketingSession.STATUS_UNKNOWN_ERROR:
                            handleAppEvents(AppState.WAIT_CARD_SALES_START, null);
                            displayMessage("Unknown error while loading ticket", 2000);
                            break;
                        case TicketingSession.STATUS_CARD_SWITCHED:
                            handleAppEvents(AppState.PAYMENT, null);
                            displayMessage("The card has been switched", 0);
                            break;
                        case TicketingSession.STATUS_SESSION_ERROR:
                            handleAppEvents(AppState.WAIT_CARD_SALES_START, null);
                            displayMessage("Session error while loading ticket", 2000);
                            break;
                        default:
                            handleAppEvents(AppState.WAIT_CARD_SALES_START, null);
                            displayMessage("Error " + loadStatus + " while loading ticket", 2000);
                            break;
                    }
                } else {
                    displayMessage("Put the card back on the reader", 0);
                }
            } catch (Throwable t) {
                displayMessage("Error while loading ticket", 2000);
            }
        }
    }

    /**
     * handle the new sale button event: reinit the sale process
     *
     * @param mouseEvent
     */
    public void onNewSaleBtnClicked(MouseEvent mouseEvent) throws KeypleReaderNotFoundException {
        handleAppEvents(AppState.WAIT_CARD_SALES_START, null);
    }

    /**
     * display the PO reader name
     *
     * @param name
     */
    private void setPoReaderName(String name) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                lblPOreader.setText(name);
            }
        });
    }

    /**
     * display the SAM reader name
     *
     * @param name
     */
    private void setSamReaderName(String name) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                lblSAMreader.setText(name);
            }
        });
    }

    /**
     * activate the view whose index is provided as an argument
     *
     * @param idx
     */
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

    /**
     * fix the text of a label
     *
     * @param label
     * @param text
     */
    private void setLabel(Label label, String text) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                label.setText(text);
            }
        });
    }

    /**
     * display a message for the time specified by delay if delay is 0, then display indefinitely
     *
     * @param text
     * @param delay
     */
    private void displayMessage(String text, int delay) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                setLabel(lblMessage, text);
            }
        });
        if (delay != 0) {
            Timer timer = new Timer();
            TimerTask task = new TimerTask() {
                public void run() {
                    setLabel(lblMessage, "");
                }
            };
            timer.schedule(task, delay);
        }
    }

    /**
     * display the PO technical info string
     *
     * @param text
     */
    private void displayPo(String text) {
        setLabel(lblPo, text);
    }

    private void displayCardInfo(String holderName, String contracts, String counters, String log) {
        setLabel(lblHolderName, holderName);
        setLabel(lblContracts, contracts);
        setLabel(lblCounters, counters);
        setLabel(lblLastTransaction, log);
    }

    /**
     * display the PO applicative data
     *
     * @param cardContent
     */
    private void displayCardInfo(CardContent cardContent) {
        byte[] env = cardContent.getEnvironment().get(1);
        if(env != null) {
            setLabel(lblHolderName, new String(env));
        } else {
            setLabel(lblHolderName, "");
        }
        byte[] contract = cardContent.getContracts().get(1);
        if(contract != null) {
            setLabel(lblContracts, new String(contract));
        } else {
            setLabel(lblContracts, "");
        }
        Integer counter = cardContent.getCounters().get(0);
        if(counter != null) {
            setLabel(lblCounters, counter.toString());
        } else {
            setLabel(lblCounters, "");
        }
        String log = "";
        for (int i = 0; i < cardContent.getEventLog().size(); i++) {
            byte[] l = cardContent.getEventLog().get(i + 1);
            if(l != null) {
                log += "[" + (-i) + "]: " + new String(l);
            }
            if (i < (cardContent.getEventLog().size() - 1)) {
                log += "     ";
            }
        }
        setLabel(lblLastTransaction, log);
    }

    /**
     * main app state machine handle
     *
     * @param appState
     * @param readerEvent
     */
    private void handleAppEvents(AppState appState, ReaderEvent readerEvent) {
        logger.info("Current state = {}, wanted new state = {}, event = {}", currentAppState, appState,
                readerEvent == null ? "null" : readerEvent.getEventType());
        /* clear message */
        displayMessage("", 0);
        if (readerEvent != null && readerEvent.getEventType().equals(ReaderEvent.EventType.SE_INSERTED)) {
            if (appState == AppState.WAIT_SYSTEM_READY) {
                return;
            }
            logger.info("Process default selection...");
            if (ticketingSession == null) {
                logger.error("The ticketingSession is null.");
                return;
            }
            if (!ticketingSession.processDefaultSelection(readerEvent.getDefaultSelectionResponse())) {
                logger.error("PO Not selected");
                displayMessage("Card not supported in this demo", 1000);
                return;
            }
            if (!ticketingSession.getPoTypeName().equals("CALYPSO")) {
                displayMessage(ticketingSession.getPoTypeName() + " card" +
                        " not supported in this demo", 1000);
            } else {
                displayPo("S/N " + ticketingSession.getPoIdentification());
                logger.info("A Calypso PO selection succeeded.");
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
                        if (ticketingSession.analyzePoProfile()) {
                            activatePane(PANE_FILL_CART);
                            displayCardInfo(ticketingSession.getCardContent());
                            currentAppState = AppState.FILL_CART;
                        }
                    } else {
                        /* personalisation */
                        if (ticketingSession.personalize(comboWorkMode.getValue().toString())) {
                            displayMessage("The PO has been personalized!", 3000);
                            activatePane(PANE_WAIT_CARD_SALES_START);
                            currentAppState = AppState.WAIT_CARD_SALES_START;
                        } else {
                            displayMessage("The personalization failed!", 3000);
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
                /* launch keyple initialization slightly delayed to let the user see the initialization screen */
                Timer timer = new Timer();
                TimerTask task = new TimerTask() {
                    public void run() {
                        handleAppEvents(AppState.SALES_RECEIPT, null);
                    }
                };
                timer.schedule(task, 1000L);
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
                displayPo("-");
            }
        }
        logger.info("New state = {}", currentAppState);
    }

    /**
     * load the PO according to the choice provided as an argument
     *
     * @param choice
     * @return
     * @throws KeypleReaderException
     */
    private int load(int choice) throws KeypleReaderException {
        int status = TicketingSession.STATUS_UNKNOWN_ERROR;
        switch (choice) {
            case 0:
                status = ticketingSession.loadTickets(1);
                break;
            case 1:
                status = ticketingSession.loadTickets(10);
                break;
            case 2:
//                poTransaction.prepareUpdateRecordCmd(CalypsoInfo.SFI_Contracts, CalypsoInfo.RECORD_NUMBER_1,
//                        "SEASON  TICKET 1             ".getBytes(), "Season ticket");
//
                break;
        }

        return status;
    }

    /**
     * initialize the PO reader, prepare the default selection called on PO reader connection event
     *
     * @param reader
     * @throws KeypleBaseException
     */
    private void initializePoReader(SeReader reader) throws KeypleBaseException {
        if (reader != null) {
            poReaderName = reader.getName();
            setPoReaderName(poReaderName);
            if (poReaderObserver == null) {
                poReaderObserver = new PoReaderObserver();
            }
            ((ObservableReader) reader).addObserver(poReaderObserver);
            reader.setParameter(PcscReader.SETTING_KEY_LOGGING, "true");
            reader.setParameter(PcscReader.SETTING_KEY_PROTOCOL, PcscReader.SETTING_PROTOCOL_T1);
            /* Set the PO reader protocol flag for ISO14443 */
            reader.addSeProtocolSetting(
                    new SeProtocolSetting(PcscProtocolSetting.SETTING_PROTOCOL_ISO14443_4));
            /* Set the PO reader protocol flag for Mifare Classic */
            reader.addSeProtocolSetting(
                    new SeProtocolSetting(PcscProtocolSetting.SETTING_PROTOCOL_MIFARE_CLASSIC));
            /* change appState if both readers are readersReady */
            if (ticketingSessionManager != null) {
                ticketingSession = ticketingSessionManager.createTicketingSession(reader);
                handleAppEvents(AppState.WAIT_CARD_SALES_START, null);
            }
        } else {
            throw new IllegalStateException("reader is null");
        }
    }

    /**
     * deinitialize the PO reader called on PO reader disconnection event
     *
     * @param reader
     */
    private void deInitializePoReader(SeReader reader) throws KeypleReaderNotFoundException {
        if (reader != null) {
            setPoReaderName(STR_NOT_CONNECTED);
            ((ObservableReader) reader).removeObserver(poReaderObserver);
            handleAppEvents(AppState.WAIT_SYSTEM_READY, null);
            ticketingSessionManager.destroyTicketingSession(reader.getName());
            poReaderName = "";
        } else {
            throw new IllegalStateException("reader is null");
        }
    }

    /**
     * initialize the SAM reader, prepare the default selection called on SAM reader connection event
     *
     * @param reader
     * @throws KeypleBaseException
     */
    private void initializeSamReader(SeReader reader) {
        if (reader != null) {
            samReader = reader;
            setSamReaderName(samReader.getName());
            if (samReaderObserver == null) {
                samReaderObserver = new SamReaderObserver();
            }
            ((ObservableReader) samReader).addObserver(samReaderObserver);
            try {
                samReader.setParameter(PcscReader.SETTING_KEY_LOGGING, "true");
                samReader.setParameter(PcscReader.SETTING_KEY_PROTOCOL, PcscReader.SETTING_PROTOCOL_T0);
            } catch (KeypleBaseException e) {
                e.printStackTrace();
            }
        } else {
            throw new IllegalStateException("reader is null");
        }
    }

    /**
     * deinitialize the SAM reader called on SAM reader disconnection event
     *
     * @param reader
     */
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

    /**
     * Ask for the role to give to a reader
     *
     * @param reader
     */
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
                        txtPoReaderFilter.setText(poReaderName);
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

    /**
     * Initialize the SAM channel
     *
     * @param samReader
     */
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

    /**
     * handle the restart event (Calypso image click)
     *
     * @param mouseEvent
     */
    public void onRestartBtnClicked(MouseEvent mouseEvent) {
        handleAppEvents(AppState.WAIT_CARD_SALES_START, null);
    }

    /**
     * handle the log output to the dedicated text area
     */
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

    /**
     * handle the plugin events
     */
    private class PcscPluginObserver implements PluginObserver {

        @Override
        public void update(PluginEvent pluginEvent) {
            SeReader reader = null;
            logger.info("PluginEvent: PLUGINNAME = {}, READERNAME = {}, EVENTTYPE = {}",
                    pluginEvent.getPluginName(), pluginEvent.getReaderName(), pluginEvent.getEventType());

            try {
                reader = SeProxyService.getInstance().getPlugin(pluginEvent.getPluginName())
                        .getReader(pluginEvent.getReaderName());
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
                        if (poReaderName.equals(reader.getName())) {
                            deInitializePoReader(reader);
                        } else {
                            if (samReader != null && samReader.getName().equals(reader.getName())) {
                                deInitializeSamReader(reader);
                            }
                        }
                        break;

                    default:
                        logger.info("Unexpected reader event. EVENT = {}",
                                pluginEvent.getEventType().getName());
                        break;
                }
            } catch (KeyplePluginNotFoundException e) {
                e.printStackTrace();
            } catch (KeypleReaderNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * handle the PO reader events
     */
    private class PoReaderObserver implements ReaderObserver {

        @Override
        public void update(ReaderEvent readerEvent) {
            logger.info("PO Reader event = {}", readerEvent.getEventType());
            handleAppEvents(currentAppState, readerEvent);
        }
    }

    /**
     * handle the SAM reader events
     */
    private class SamReaderObserver implements ReaderObserver {

        @Override
        public void update(ReaderEvent readerEvent) {
            logger.info("SAM Reader event = {}", readerEvent.getEventType());
            if (readerEvent.getEventType() == ReaderEvent.EventType.SE_INSERTED) {
                /* SAM open logical channel */
                checkSamAndOpenChannel(samReader);
                ticketingSessionManager = new TicketingSessionManager(samReader);
                /* change appState if both readers are readersReady */
                if (!poReaderName.isEmpty()) {
                    try {
                        ticketingSession =
                                ticketingSessionManager.createTicketingSession(SeProxyService.getInstance().getPlugins().first().getReader(poReaderName));
                        handleAppEvents(AppState.WAIT_CARD_SALES_START, null);
                    } catch (KeypleReaderNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
