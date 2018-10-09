/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.plugin.remote_se.nse;

import org.eclipse.keyple.seproxy.SeRequestSet;
import org.eclipse.keyple.seproxy.SeResponseSet;
import org.eclipse.keyple.seproxy.exception.KeypleReaderException;

interface NseAPI {

    SeResponseSet onTransmit(String sessionId, SeRequestSet req) throws KeypleReaderException;

    // String onGetName();

    // Boolean onIsSePresent();

    // void onAddSeProtocolSetting(SeProtocolSetting seProtocolSetting);


}
