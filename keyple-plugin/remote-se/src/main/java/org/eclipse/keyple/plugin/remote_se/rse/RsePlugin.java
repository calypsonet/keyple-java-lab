/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.plugin.remote_se.rse;

import org.eclipse.keyple.plugin.remote_se.transport.DtoSender;
import org.eclipse.keyple.seproxy.ProxyReader;
import org.eclipse.keyple.seproxy.ReaderPlugin;
import org.eclipse.keyple.seproxy.event.ObservablePlugin;
import org.eclipse.keyple.seproxy.event.PluginEvent;
import org.eclipse.keyple.seproxy.event.ReaderEvent;
import org.eclipse.keyple.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.seproxy.exception.KeypleReaderNotFoundException;
import org.eclipse.keyple.util.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Remote SE Plugin
 * Creates a virtual reader when a remote readers connect
 * Manages the dispatch of events received from remote readers
 */
public class RsePlugin extends Observable implements ObservablePlugin {

    private static final Logger logger = LoggerFactory.getLogger(RsePlugin.class);

    //private final VirtualSeSessionManager sessionManager;

    // virtal readers
    private final SortedSet<RseReader> rseReaders = new TreeSet<RseReader>();

    private VirtualSeSessionManager sessionManager;
    /**
     * Only {@link VirtualSeRemoteService} can instiancite a RsePlugin
     */
    RsePlugin(VirtualSeSessionManager sessionManager) {
        this.sessionManager = sessionManager;
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
            if (RseReader.getNativeReaderName().equals(remoteName)) {
                return RseReader;
            }
        }
        throw new KeypleReaderNotFoundException(remoteName);
    }

    /**
     * Create a virtual reader
     *
     */
    protected ProxyReader createVirtualReader(String clientNodeId, String nativeReaderName, DtoSender dtoSender) throws KeypleReaderException {
        logger.debug("createVirtualReader for nativeReader {}", nativeReaderName);

        //create a new session for the new reader
        IReaderSession session  = sessionManager.createSession(nativeReaderName,clientNodeId);

        //DtoSender sends Dto when the session requires to
        ((ReaderSessionImpl) session).addObserver(dtoSender);

        // check if reader is not already connected (by localReaderName)
        if (!isReaderConnected(nativeReaderName)) {
            logger.info("Connecting a new RemoteSeReader with localReaderName {} with session {}", nativeReaderName,
                    session.getSessionId());

            RseReader rseReader = new RseReader(session, nativeReaderName);
            rseReaders.add(rseReader);
            notifyObservers(new PluginEvent(getName(), rseReader.getName(),
                    PluginEvent.EventType.READER_CONNECTED));
            logger.info("*****************************");
            logger.info(" CONNECTED {} ", rseReader.getName());
            logger.info("*****************************");
            return rseReader;
        } else {
            throw  new KeypleReaderException("Virtual Reader already exists");
        }
    }

    /**
     * Delete a virtual reader
     *
     * @param nativeReaderName
     * @return
     */
    protected void disconnectRemoteReader(String nativeReaderName) throws KeypleReaderNotFoundException {
        logger.debug("disconnectRemoteReader {}", nativeReaderName);

        // check if reader is not already connected (by name)
        if (isReaderConnected(nativeReaderName)) {
            logger.info("DisconnectRemoteReader RemoteSeReader with name {} with session {}", nativeReaderName);

            //retrieve virtual reader to delete
            RseReader virtualReader = (RseReader) this.getReaderByRemoteName(nativeReaderName);

            //remove observers
            ((ReaderSessionImpl) virtualReader.getSession()).clearObservers();

            //remove reader
            rseReaders.remove(virtualReader);

            //notify of disconnect
            notifyObservers(new PluginEvent(getName(), nativeReaderName,
                    PluginEvent.EventType.READER_DISCONNECTED));

            logger.info("*****************************");
            logger.info(" DISCONNECTED {} ", nativeReaderName);
            logger.info("*****************************");

        } else {
            logger.warn("No remoteSeReader with name {} found", nativeReaderName);
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
            if (RseReader.getNativeReaderName().equals(name)) {
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



}
