/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.plugin.remotese.transport;


/**
 * Message DTO with an embedded KeypleDto enriched with common layer information
 * For instance, can embbed web socket connection information
 */
public interface TransportDto {


    /**
     * Retrieve the embedded Keyple DTO
     * 
     * @return embedded Keyple DTO
     */
    KeypleDto getKeypleDTO();

    /**
     * Embed a Keyple DTO into a new TransportDto with common information
     * 
     * @param kdto : keyple DTO to be embedded
     * @return Transport DTO with embedded keyple DTO
     */
    TransportDto nextTransportDTO(KeypleDto kdto);


    /**
     * Get the sender Object to send back a response if needed
     * 
     * @return
     */
    DtoSender getDtoSender();



}
