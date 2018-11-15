/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.example.remote.common;

import java.io.IOException;

/**
 * Factory for Clients and Servers sharing a protocol and a configuration to connectAReader each others
 */
public abstract class TransportFactory {

    abstract public ClientNode getClient(Boolean isMaster);

    abstract public ServerNode getServer(Boolean isMaster) throws IOException;




}
