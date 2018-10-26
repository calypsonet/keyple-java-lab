/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.plugin.remote_se.rse;

import org.eclipse.keyple.seproxy.*;
import org.eclipse.keyple.seproxy.event.ObservableReader;
import org.eclipse.keyple.seproxy.event.PluginEvent;
import org.eclipse.keyple.seproxy.event.ReaderEvent;
import org.eclipse.keyple.seproxy.protocol.SeProtocolSetting;
import org.eclipse.keyple.util.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Virtual Reader
 * Behaves like the Remote Reader it emulates
 */
public class RseReader extends Observable implements ObservableReader {

    private final IReaderSession session;
    private final String remoteName;
    private final String name;

    private static final Logger logger = LoggerFactory.getLogger(RseReader.class);

    /**
     * Called by {@link RsePlugin}
     * @param session
     * @param remoteName
     */
    RseReader(IReaderSession session, String remoteName) {
        this.session = session;
        this.remoteName = remoteName;
        this.name = "remote-" + remoteName;
    }

    /**
     * Local name of the virtual reader
     * @return
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Name of the Native Reader
     * @return
     */
    public String getNativeReaderName() {
        return remoteName;
    }

    IReaderSession getSession() {
        return session;
    }

    @Override
    public boolean isSePresent() {
        logger.error("isSePresent is not implemented yet");
        return false;// not implemented
    }

    /**
     * Blocking Transmit
     *
     * @param seRequestSet : SeRequestSe to be transmitted
     * @return seResponseSet : SeResponSet
     * @throws IllegalArgumentException
     */
    @Override
    public SeResponseSet transmit(SeRequestSet seRequestSet) throws IllegalArgumentException {
        return session.transmit(this.getNativeReaderName(), this.getName(), seRequestSet);
    }

    @Override
    public SeResponse transmit(SeRequest seApplicationRequest) throws IllegalArgumentException {
        return  session.transmit(this.getNativeReaderName(), this.getName(), new SeRequestSet(seApplicationRequest))
                .getSingleResponse();
    }


    /**
     * Non blocking Transmit
     * 
     * @param seRequestSet : SeRequestSe to be transmitted
     * @param seResponseSetCallback : callback to get the seResponseSet
     */
    public void asyncTransmit(SeRequestSet seRequestSet,
            ISeResponseSetCallback seResponseSetCallback) throws IllegalArgumentException {
        session.asyncTransmit(this.getNativeReaderName(), this.getName(), seRequestSet, seResponseSetCallback);
    }

    @Override
    public void addSeProtocolSetting(SeProtocolSetting seProtocolSetting) {
        logger.error("addSeProtocolSetting is not implemented yet");

    }

    /*
    PACKAGE PRIVATE
     */

    /**
     * When an event occurs on the Remote LocalReader, notify Observers
     * 
     * @param event
     */
    void onRemoteReaderEvent(final ReaderEvent event) {
        final RseReader thisReader = this;
        logger.info("*****************************");
        logger.info(" EVENT {} ", event.getEventType());
        logger.info("*****************************");
        //notify observers in a separate thread
        new Thread() {
            public void run() {
                thisReader.notifyObservers(event);
            }
        }.start();

    }


    /**
     *
     * HELPERS
     */


    // compare by name
    @Override
    public int compareTo(ProxyReader o) {
        return o.getName().compareTo(this.getName());
    }// todo

    @Override
    public Map<String, String> getParameters() {
        logger.error("getParameters is not implemented yet");
        return null;
    }

    @Override
    public void setParameter(String key, String value) throws IllegalArgumentException {
        logger.error("setParameter is not implemented yet");
    }

    @Override
    public void setParameters(Map<String, String> parameters) throws IllegalArgumentException {
        logger.error("setParameters is not implemented yet");

    }


    /**
     * Add an observer. This will allow to be notified about all readers or plugins events.
     *
     * @param observer Observer to notify
     */

    public void addObserver(ReaderObserver observer) {
        logger.trace("[{}][{}] addObserver => Adding an observer.", this.getClass(),
                this.getName());
        super.addObserver(observer);
    }

    /**
     * Remove an observer.
     *
     * @param observer Observer to stop notifying
     */

    public void removeObserver(ReaderObserver observer) {
        logger.trace("[{}] removeObserver => Deleting a reader observer", this.getName());
        super.removeObserver(observer);
    }



    /**
     * This method shall be called only from a SE Proxy plugin or reader implementing
     * AbstractObservableReader or AbstractObservablePlugin. Push a ReaderEvent / PluginEvent of the
     * selected AbstractObservableReader / AbstractObservablePlugin to its registered Observer.
     *
     * @param event the event
     */

    public final void notifyObservers(ReaderEvent event) {
        logger.trace("[{}] AbstractObservableReader => Notifying a reader event: ", this.getName(),
                event);
        setChanged();
        super.notifyObservers(event);

    }



}
