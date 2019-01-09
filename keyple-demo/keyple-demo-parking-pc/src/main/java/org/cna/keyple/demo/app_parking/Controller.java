package org.cna.keyple.demo.app_parking;

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
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import org.cna.keyple.demo.app_parking.utils.StaticOutputStreamAppender;
import org.cna.keyple.demo.ticketing.CalypsoInfo;
import org.cna.keyple.demo.ticketing.CardContent;
import org.cna.keyple.demo.ticketing.TicketingSession;
import org.cna.keyple.demo.ticketing.TicketingSessionManager;
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
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

public class Controller implements Initializable {
    private final static String PREF_PO_READER_FILTER = "PO_READER_FILTER";
    private final static String PREF_SAM_READER_FILTER = "SAM_READER_FILTER";
    private final static String PREF_IGNORE_READER_FILTER = "IGNORE_READER_FILTER";
    private static final String STR_NOT_CONNECTED = "not connected";
    private static final int PANE_WAIT_SYSTEM_READY = 0;
    private static final int PANE_WAIT_CARD = 1;
    private static final int PANE_CARD_STATUS = 2;
    public Label lblPo;
    public Label lblMessage;
    public ComboBox comboLogLevel;
    public Label lblHolderName;
    public Label lblCounters;
    public Label lblContracts;
    public Label lblStatus;
    public ImageView imgAuthorized;
    public ImageView imgUnauthorized;
    private TicketingSessionManager ticketingSessionManager;
    private TicketingSession ticketingSession;

    /* application states */
    private enum AppState {
        UNSPECIFIED, WAIT_SYSTEM_READY, WAIT_CARD, CARD_STATUS
    }

    ObservableList<String> logLevelList = FXCollections.observableArrayList("TRACE", "DEBUG", "INFO", "ERROR");

    final Logger logger = (Logger) LoggerFactory.getLogger(Controller.class);
    public Label lblLastTransaction;
    public Label lblConnectReaders;
    public Button btnClearTaLogs;
    public TextArea taLogs;
    public Label lblPOreader;
    public Label lblSAMreader;
    public TextField txtPoReaderFilter;
    public TextField txtSamReaderFilter;
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
     * Clears the ignored readers list
     * @param mouseEvent
     */
    public void onResetIgnoredReadersListBtnClicked(MouseEvent mouseEvent) {
        pref.put(PREF_IGNORE_READER_FILTER, "");
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


    private void displayStatus(String text) {
        setLabel(lblStatus, text);
    }
    /**
     * display the PO applicative data
     *
     * @param cardContent
     */
    private void displayCardInfo(CardContent cardContent) {
        byte[] env = cardContent.getEnvironment().get(1);
        if (env != null) {
            setLabel(lblHolderName, new String(env));
        } else {
            setLabel(lblHolderName, "");
        }
        byte[] contract = cardContent.getContracts().get(1);
        if (contract != null) {
            setLabel(lblContracts, new String(contract));
        } else {
            setLabel(lblContracts, "");
        }
//        Integer counter = cardContent.getCounters().get(0);
//        if(counter != null) {
//            setLabel(lblCounters, counter.toString());
//        } else {
//            setLabel(lblCounters, "");
//        }
        String log = "";
        for (int i = 0; i < cardContent.getEventLog().size(); i++) {
            byte[] l = cardContent.getEventLog().get(i + 1);
            if (l != null) {
                log += "[" + (-i) + "]: " + new String(l);
            }
            if (i < (cardContent.getEventLog().size() - 1)) {
                log += "     ";
            }
        }
        log = log.replace("T1", "Parking validation");
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
        if (readerEvent != null) {
            if (readerEvent.getEventType().equals(ReaderEvent.EventType.SE_INSERTED)) {
                if (appState == AppState.WAIT_SYSTEM_READY) {
                    return;
                }
                logger.info("Process default selection...");
                if (ticketingSession == null) {
                    logger.error("The ticketingSession is null.");
                    return;
                }
                imgAuthorized.setVisible(false);
                imgUnauthorized.setVisible(false);
                if (!ticketingSession.processDefaultSelection(readerEvent.getDefaultSelectionResponse())) {
                    logger.error("PO Not selected");
                    imgUnauthorized.setVisible(true);
                    displayStatus("Card not supported in this demo");
                    activatePane(PANE_CARD_STATUS);
                    return;
                }
                if (!ticketingSession.getPoTypeName().equals("CALYPSO")) {
                    imgUnauthorized.setVisible(true);
                    displayStatus(ticketingSession.getPoTypeName() + " card" +
                            " not supported in this demo");
                    activatePane(PANE_CARD_STATUS);
                } else {
                    displayPo("S/N " + ticketingSession.getPoIdentification());
                    logger.info("A Calypso PO selection succeeded.");
                    appState = AppState.CARD_STATUS;
                }
            } else if (readerEvent.getEventType().equals(ReaderEvent.EventType.SE_REMOVAL)) {
                currentAppState = AppState.WAIT_SYSTEM_READY;
                Timer timer = new Timer();
                TimerTask task = new TimerTask() {
                    public void run() {
                        handleAppEvents(AppState.WAIT_CARD, null);
                    }
                };
                timer.schedule(task, 1000);
            }
        }
        switch (appState) {
            case WAIT_SYSTEM_READY:
                activatePane(PANE_WAIT_SYSTEM_READY);
                currentAppState = appState;
                break;
            case WAIT_CARD:
                if (currentAppState != appState) {
                    activatePane(PANE_WAIT_CARD);
                    currentAppState = appState;
                }
                break;
            case CARD_STATUS:
                currentAppState = AppState.CARD_STATUS;
                if (readerEvent != null && readerEvent.getEventType().equals(ReaderEvent.EventType.SE_INSERTED)) {
                    try {
                        if (ticketingSession.analyzePoProfile()) {
                            displayCardInfo(ticketingSession.getCardContent());
                            String contract = "";
                            CardContent cardContent = ticketingSession.getCardContent();
                            if(cardContent.getContracts().get(1) != null) {
                                contract = new String(cardContent.getContracts().get(1));
                            }
                            logger.info("Contract = {}", contract);
                            if(contract.isEmpty() || contract.contains("NO CONTRACT") || !contract.contains(
                                    "SEASON")) {
                                imgUnauthorized.setVisible(true);
                                displayStatus("No Valid Pass\nPlease pay the parking fees\nor reload your " +
                                        "transportation card.");
                            } else {
                                imgAuthorized.setVisible(true);
                                displayStatus("Valid Season Pass: " + contract + "\nFree Access until the " +
                                        "validity end date.");
                            }
                            switch (ticketingSession.loadTickets(0)) {
                                case TicketingSession.STATUS_OK:
                                    break;
                                case TicketingSession.STATUS_UNKNOWN_ERROR:
                                case TicketingSession.STATUS_CARD_SWITCHED:
                                    imgUnauthorized.setVisible(true);
                                    displayStatus("Unknown error");
                                    break;
                                case TicketingSession.STATUS_SESSION_ERROR:
                                    imgUnauthorized.setVisible(true);
                                    displayStatus("Secure Session error");
                                    break;
                            }
                        }
                    } catch (KeypleReaderException e) {
                        imgUnauthorized.setVisible(true);
                        displayStatus("Communication error");
                        e.printStackTrace();
                    } catch (IllegalStateException e) {
                        imgUnauthorized.setVisible(true);
                        displayStatus("Communication error");
                        e.printStackTrace();
                    }
                    activatePane(PANE_CARD_STATUS);
                 }
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
            logger.info("PO reader: " + poReaderName);
            ((ObservableReader) reader).addObserver(poReaderObserver);
            reader.setParameter(PcscReader.SETTING_KEY_LOGGING, "true");
            reader.setParameter(PcscReader.SETTING_KEY_PROTOCOL, PcscReader.SETTING_PROTOCOL_T1);
            /* Set the PO reader protocol flag for ISO14443 */
            reader.addSeProtocolSetting(
                    new SeProtocolSetting(PcscProtocolSetting.SETTING_PROTOCOL_ISO14443_4));
            /* Set the PO reader protocol flag for Mifare Classic */
            reader.addSeProtocolSetting(
                    new SeProtocolSetting(PcscProtocolSetting.SETTING_PROTOCOL_MIFARE_CLASSIC));
            /* Set the PO reader protocol flag for Mifare Desfire */
            reader.addSeProtocolSetting(
                    new SeProtocolSetting(PcscProtocolSetting.SETTING_PROTOCOL_MIFARE_DESFIRE));
            /* change appState if both readers are readersReady */
            if (ticketingSessionManager != null) {
                ticketingSession = ticketingSessionManager.createTicketingSession(reader);
                handleAppEvents(AppState.WAIT_CARD, null);
            } else {
                logger.info("ticketingSessionManager is null");
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
            logger.info("SAM reader: " + samReader.getName());
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
                ButtonType buttonTypeIgnore = new ButtonType("Ignore permanently");
                ButtonType buttonTypeCancel = new ButtonType("Ignore", ButtonBar.ButtonData.CANCEL_CLOSE);

                alert.getButtonTypes().setAll(buttonTypePo, buttonTypeSam, buttonTypeIgnore, buttonTypeCancel);

                Optional<ButtonType> result = alert.showAndWait();
                try {
                    if (result.get() == buttonTypePo) {
                        logger.info("Initialize PO reader...");
                        initializePoReader(reader);
                        txtPoReaderFilter.setText(poReaderName);
                    } else if (result.get() == buttonTypeSam) {
                        logger.info("Initialize SAM reader...");
                        initializeSamReader(reader);
                        txtSamReaderFilter.setText(samReader.getName());
                    } else if (result.get() == buttonTypeIgnore) {
                        logger.info(reader.getName() + " added to the ignore list.");
                        String ignoredReader = pref.get(PREF_IGNORE_READER_FILTER, "");
                        pref.put(PREF_IGNORE_READER_FILTER, ignoredReader + reader.getName());
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
        handleAppEvents(AppState.WAIT_CARD, null);
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
                                    String ignoredReaders = pref.get(PREF_IGNORE_READER_FILTER, "");
                                    if(!ignoredReaders.contains(reader.getName())) {
                                        // ask for assignment if not in ignore list
                                        assignReaderRole(reader);
                                    }
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
                logger.debug("Create TicketingSessionManager");
                ticketingSessionManager = new TicketingSessionManager(samReader);
                if(ticketingSessionManager == null) {
                    logger.error("Creation of ticketingSessionManager failed");
                }
                /* change appState if both readers are readersReady */
                if (!poReaderName.isEmpty()) {
                    try {
                        ticketingSession =
                                ticketingSessionManager.createTicketingSession(SeProxyService.getInstance().getPlugins().first().getReader(poReaderName));
                        handleAppEvents(AppState.WAIT_CARD, null);
                    } catch (KeypleReaderNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
