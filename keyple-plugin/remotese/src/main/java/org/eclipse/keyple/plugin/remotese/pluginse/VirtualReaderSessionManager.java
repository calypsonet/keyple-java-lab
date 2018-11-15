/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.plugin.remotese.pluginse;

/**
 * Manages Master Session
 */
public class VirtualReaderSessionManager {



    /**
     * Create a new session
     * @param localreaderNode
     * @param nodeId
     * @return
     */
    public IReaderSession createSession(String localreaderNode, String nodeId){
        return new ReaderSessionImpl(generateSessionId(localreaderNode,nodeId));
    }


    /*
    PRIVATE METHODS
     */

    /**
     * Generate a unique sessionId for a new connecting localreader
     * @param localreaderNode : Local Reader Name
     * @param nodeId : Node Id from which the local reader name connect to
     * @return unique sessionId
     */
    private String generateSessionId(String localreaderNode, String nodeId) {
        return localreaderNode + nodeId + String.valueOf(System.currentTimeMillis());
    }


}
