/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.plugin.remotese.pluginse;


import org.eclipse.keyple.seproxy.SeRequestSet;
import org.eclipse.keyple.seproxy.SeResponseSet;

public interface IReaderSession {

    /**
     * Retrieve sessionId
     * @return sessionId
     */
    String getSessionId();

    /**
     * Non-blocking transmit
     * @param nativeReaderName : local reader to transmit to
     * @param virtualReaderName : virtual reader thats receive the order the transmit to
     * @param seApplicationRequest : seApplicationRequest to transmit
     * @param seResponseSet : callback that receives the SeResponseSet when received
     */
    void asyncTransmit(String nativeReaderName, String virtualReaderName, SeRequestSet seApplicationRequest, ISeResponseSetCallback seResponseSet);

    /**
     * Blocking transmit
     * @param nativeReaderName : local reader to transmit to
     * @param virtualReaderName : virtual reader thats receive the order the transmit to
     * @param seApplicationRequest : seApplicationRequest to transmit
     * @return SeResponseSet
     */
    SeResponseSet transmit(String nativeReaderName, String virtualReaderName, SeRequestSet seApplicationRequest);



    /**
     * Send response in callback
     * @param seResponseSet : receive seResponseSet to be callback
     */
    void asyncSetSeResponseSet(SeResponseSet seResponseSet);

    /**
     * Has a seRequestSet in session (being transmitted)
     * @return true if a seRequestSet is being transmitted
     */
    Boolean hasSeRequestSet();

    /**
     * Get the seRequestSet being transmitted
     * @return seRequestSet transmitted
     */
    SeRequestSet getSeRequestSet();



}
