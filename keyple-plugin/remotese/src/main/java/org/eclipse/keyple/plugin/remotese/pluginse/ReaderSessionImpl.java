/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.plugin.remotese.pluginse;

import org.eclipse.keyple.plugin.remotese.transport.KeypleDto;
import org.eclipse.keyple.plugin.remotese.transport.KeypleDtoHelper;
import org.eclipse.keyple.plugin.remotese.transport.json.JsonParser;
import org.eclipse.keyple.seproxy.SeRequestSet;
import org.eclipse.keyple.seproxy.SeResponseSet;
import org.eclipse.keyple.util.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * Manage RSE Reader Session Manage SeRequestSet to transmit and receive SeResponseSet in blocking and
 * non blocking way
 */
public class ReaderSessionImpl extends Observable<KeypleDto> implements IReaderSession {

    private static final Logger logger = LoggerFactory.getLogger(ReaderSessionImpl.class);

    private final String sessionId;
    private SeRequestSet seRequestSet;
    private ISeResponseSetCallback seResponseSetCallback;
    private CountDownLatch lock;
    private SeResponseSet seResponseSet;

    // constructor
    public ReaderSessionImpl(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Manage an asynchronous transmit, call the callback to transmit the SeResponseSet when received
     * @param seRequestSet : seRequestSet to be processed
     * @param seResponseSetCallback : callback that will be called once seResponseSet is received
     */
    public void asyncTransmit(String nativeReaderName, String virtualReaderName, SeRequestSet seRequestSet,
            ISeResponseSetCallback seResponseSetCallback) {

        logger.debug("Session {} asyncTransmit {}", sessionId, seRequestSet);
        if (this.seRequestSet == null) {
            logger.debug("Set a new seRequestSet in Session {}", sessionId);

            // used for 1way communication
            this.seRequestSet = seRequestSet;
            this.seResponseSetCallback = seResponseSetCallback;

            // used for 2way communications
            notifyObservers(new KeypleDto(KeypleDtoHelper.READER_TRANSMIT,
                    JsonParser.getGson().toJson(this.seRequestSet, SeRequestSet.class), true,
                    sessionId, nativeReaderName, virtualReaderName, null));

        } else {
            logger.warn("SeRequestSet is already set in Session {}", sessionId);

        }
    }

    /**
     * Set the SeResponseSet back into session to be transmitted to client
     * @param seResponseSet
     */
    @Override
    public void asyncSetSeResponseSet(SeResponseSet seResponseSet) {
        logger.debug("Session {} asyncSetSeResponseSet {}", sessionId, seResponseSet);
        if (this.seRequestSet == null) {
            logger.warn("seRequestSet is missing while receiving seResponseSet {}", seResponseSet);
        }

        // release seRequestSet next work
        this.seRequestSet = null;

        // set SeResponseSet in session for syncTransmit
        this.seResponseSet = seResponseSet;

        // return seResponseSet by callback
        this.seResponseSetCallback.getResponseSet(seResponseSet);
    }

    @Override
    public Boolean hasSeRequestSet() {
        return seRequestSet != null;
    }


    /**
     * Blocking transmit
     * @param nativeReaderName : local reader to transmit to
     * @param virtualReaderName : virtual reader thats receive the order the transmit to
     * @param seApplicationRequest : seApplicationRequest to transmit
     * @return
     */
    @Override
    public SeResponseSet transmit(final String nativeReaderName, final String virtualReaderName, final SeRequestSet seApplicationRequest) {
        logger.debug("Session {} sync transmit {}", sessionId, seApplicationRequest);


        Thread asyncTransmit = new Thread() {
            public void run() {
                asyncTransmit(nativeReaderName, virtualReaderName, seApplicationRequest, new ISeResponseSetCallback() {
                    @Override
                    public void getResponseSet(SeResponseSet seResponseSet) {
                        logger.debug("Receive SeResponseSetCallback, release lock ");
                        lock.countDown();
                    }
                });
            }
        };

        asyncTransmit.start();


        try {
            logger.debug("Set lock on thread");
            lock = new CountDownLatch(1);
            lock.await();
            logger.debug("Send SeRequestSet, thread unlock");
            return seResponseSet;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }


    }

    @Override
    public SeRequestSet getSeRequestSet() {
        return this.seRequestSet;
    }


    @Override
    public String getSessionId() {
        return sessionId;
    }


}
