/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.plugin.remote_se.rse;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.eclipse.keyple.plugin.remote_se.transport.*;
import org.eclipse.keyple.plugin.remote_se.transport.json.JsonParser;
import org.eclipse.keyple.seproxy.ProxyReader;
import org.eclipse.keyple.seproxy.ReaderPlugin;
import org.eclipse.keyple.seproxy.SeResponseSet;
import org.eclipse.keyple.seproxy.event.ObservablePlugin;
import org.eclipse.keyple.seproxy.event.PluginEvent;
import org.eclipse.keyple.seproxy.event.ReaderEvent;
import org.eclipse.keyple.seproxy.exception.KeypleReaderNotFoundException;
import org.eclipse.keyple.util.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class RsePlugin extends Observable implements ObservablePlugin {

    private static final Logger logger = LoggerFactory.getLogger(RsePlugin.class);

    //private final RseSessionManager sessionManager;

    // virtal readers
    private final SortedSet<RseReader> rseReaders = new TreeSet<RseReader>();

    public RsePlugin() {
        //sessionManager = new RseSessionManager();
        logger.info("RemoteSePlugin");
    }

    @Override
    public String getName() {
        return "RemoteSePlugin";
    }

    @Override
    public Map<String, String> getParameters() {
        return null;
    }

    @Override
    public void setParameter(String key, String value) throws IllegalArgumentException {}

    @Override
    public void setParameters(Map<String, String> parameters) throws IllegalArgumentException {

    }

    @Override
    public SortedSet<? extends ProxyReader> getReaders() {
        return rseReaders;
    }

    @Override
    public ProxyReader getReader(String name) throws KeypleReaderNotFoundException {
        for (RseReader RseReader : rseReaders) {
            if (RseReader.getName().equals(name)) {
                return RseReader;
            }
        }
        throw new KeypleReaderNotFoundException("reader with name not found : " + name);
    }

    public ProxyReader getReaderByRemoteName(String remoteName)
            throws KeypleReaderNotFoundException {
        for (RseReader RseReader : rseReaders) {
            if (RseReader.getRemoteName().equals(remoteName)) {
                return RseReader;
            }
        }
        throw new KeypleReaderNotFoundException(
                "reader with Remote Name not found : " + remoteName);
    }

    /**
     * Create a virtual reader
     * 
     * @param name
     * @param session
     * @return
     */
    protected void connectRemoteReader(String name, IReaderSession session) {
        logger.debug("connectRemoteReader {}", name);

        // check if reader is not already connected (by name)
        if (!isReaderConnected(name)) {
            logger.info("Connecting a new RemoteSeReader with name {} with session {}", name,
                    session.getSessionId());

            RseReader rseReader = new RseReader(session, name);
            rseReaders.add(rseReader);
            notifyObservers(new PluginEvent(getName(), rseReader.getName(),
                    PluginEvent.EventType.READER_CONNECTED));
            logger.info("*****************************");
            logger.info(" CONNECTED {} ", rseReader.getName());
            logger.info("*****************************");

        } else {
            logger.warn("RemoteSeReader with name {} is already connected", name);
        }
        // todo errors
    }

    /**
     * Delete a virtual reader
     *
     * @param name
     * @return
     */
    protected void disconnectRemoteReader(String name) throws KeypleReaderNotFoundException {
        logger.debug("disconnectRemoteReader {}", name);

        // check if reader is not already connected (by name)
        if (isReaderConnected(name)) {
            logger.info("DisconnectRemoteReader RemoteSeReader with name {} with session {}", name);

            rseReaders.remove(this.getReaderByRemoteName(name));

            notifyObservers(new PluginEvent(getName(), name,
                    PluginEvent.EventType.READER_DISCONNECTED));

            logger.info("*****************************");
            logger.info(" DISCONNECTED {} ", name);
            logger.info("*****************************");

        } else {
            logger.warn("RemoteSeReader with name {} is already connected", name);
        }
        // todo errors
    }

    protected void onReaderEvent(ReaderEvent event, String sessionId) {
        logger.debug("OnReaderEvent {}", event);
        logger.debug("Dispatch ReaderEvent to the appropriate Reader {} {}", event.getReaderName(),
                sessionId);
        try {
            // todo dispatch is managed by name, should take sessionId also
            RseReader rseReader = (RseReader) getReaderByRemoteName(event.getReaderName());
            rseReader.onRemoteReaderEvent(event);

        } catch (KeypleReaderNotFoundException e) {
            e.printStackTrace();
        }

    }



    /**
     * Add an observer. This will allow to be notified about all readers or plugins events.
     *
     * @param observer Observer to notify
     */

    public void addObserver(ObservablePlugin.PluginObserver observer) {
        logger.trace("[{}][{}] addObserver => Adding an observer.", this.getClass(),
                this.getName());
        super.addObserver(observer);
    }

    /**
     * Remove an observer.
     *
     * @param observer Observer to stop notifying
     */

    public void removeObserver(ObservablePlugin.PluginObserver observer) {
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

    public final void notifyObservers(PluginEvent event) {
        logger.trace("[{}] AbstractObservableReader => Notifying a plugin event: ", this.getName(),
                event);
        setChanged();
        super.notifyObservers(event);

    }

    private Boolean isReaderConnected(String name) {
        for (RseReader RseReader : rseReaders) {
            if (RseReader.getRemoteName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    // todo
    @Override
    public int compareTo(ReaderPlugin o) {
        return 0;
    }





    protected TransportDto isSeRequestToSendBack(TransportDto tdto) {
        TransportDto out = null;
        try {
            // retrieve reader by session
            RseReader rseReader = this.getReaderBySessionId(tdto.getKeypleDTO().getSessionId());

            if (rseReader.getSession().isAsync()
                    && ((IReaderAsyncSession) rseReader.getSession()).hasSeRequestSet()) {

                // send back seRequestSet
                out = tdto.nextTransportDTO(new KeypleDto(KeypleDtoHelper.READER_TRANSMIT,
                        JsonParser.getGson().toJson(
                                ((IReaderAsyncSession) rseReader.getSession()).getSeRequestSet()),
                        true, rseReader.getSession().getSessionId()));
            } else {
                // no response
                out = tdto.nextTransportDTO(KeypleDtoHelper.NoResponse());
            }

        } catch (KeypleReaderNotFoundException e) {
            logger.debug("Reader was not found by session", e);
            out = tdto.nextTransportDTO(KeypleDtoHelper.ErrorDTO());
        }

        return out;
    }


    protected RseReader getReaderBySessionId(String sessionId) throws KeypleReaderNotFoundException {
        for (RseReader reader : rseReaders) {
            if (reader.getSession().getSessionId().equals(sessionId)) {
                return reader;
            }
        }
        throw new KeypleReaderNotFoundException(
                "Reader sesssion was not found for session : " + sessionId);
    }
}
