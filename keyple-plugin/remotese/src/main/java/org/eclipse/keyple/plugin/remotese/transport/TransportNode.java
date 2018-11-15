/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.plugin.remotese.transport;


/**
 * TransportNode is a one-point gateway for incoming and outgoing TransportDto.
 * It extend DtoSender thus sends KeypleDto and contains a DtoHandler for incoming KeypleDto
 */
public interface TransportNode extends DtoSender {

    void setDtoHandler(DtoHandler receiver);

}
