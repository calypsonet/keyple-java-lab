/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.plugin.remotese.transport;

import org.eclipse.keyple.util.Observable;

/**
 * Components that sends a DTO over the network to the other end. (slave or master)
 * It can be an observer for KeypleDto to propagate them through the network
 */
public interface DtoSender extends Observable.Observer<KeypleDto> {

    /**
     * Send DTO with common information
     * 
     * @param message to be sent
     */
    void sendDTO(TransportDto message);

    /**
     * Send DTO with no common information (usually a new message)
     * 
     * @param message to be sent
     */
    void sendDTO(KeypleDto message);

    /**
     * get nodeId of this DtoSender, must identify the terminal. ie : androidDevice2
     * @return : nodeId
     */
    String getNodeId();

}
