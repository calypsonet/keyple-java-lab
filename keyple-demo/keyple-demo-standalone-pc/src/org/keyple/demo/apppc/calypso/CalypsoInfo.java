/********************************************************************************
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information regarding copyright
 * ownership.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.keyple.demo.apppc.calypso;


import org.eclipse.keyple.calypso.transaction.PoTransaction;

import java.util.EnumMap;

/**
 * Helper class to provide specific elements to handle Calypso cards.
 * <ul>
 * <li>AID application selection (default Calypso AID)</li>
 * <li>SAM_C1_ATR_REGEX regular expression matching the expected C1 SAM ATR</li>
 * <li>Files infos (SFI, rec number, etc) for
 * <ul>
 * <li>Environment and Holder</li>
 * <li>Event Log</li>
 * <li>Contract List</li>
 * <li>Contracts</li>
 * </ul>
 * </li>
 * </ul>
 */
public class CalypsoInfo {
    /** AID 1TIC.ICA* */
    public final static String AID = "315449432e494341";
    /** Audit-C0 */
    //public final static String AID = "315449432E4943414C54";
    /// ** 1TIC.ICA AID */
    // public final static String AID = "315449432E494341";
    /** SAM C1 regular expression: platform, version and serial number values are ignored */
    public final static String SAM_C1_ATR_REGEX =
            "3B3F9600805A[0-9a-fA-F]{2}80.1[0-9a-fA-F]{14}829000";

    public final static String ATR_REV1_REGEX = "3B8F8001805A0A0103200311........829000..";

    public final static byte RECORD_NUMBER_1 = 1;
    public final static byte RECORD_NUMBER_2 = 2;
    public final static byte RECORD_NUMBER_3 = 3;
    public final static byte RECORD_NUMBER_4 = 4;

    public final static byte SFI_EnvironmentAndHolder = (byte) 0x07;
    public final static byte SFI_EventLog = (byte) 0x08;
    public final static byte SFI_ContractList = (byte) 0x1E;
    public final static byte SFI_Contracts = (byte) 0x09;
    public final static byte SFI_Counter = (byte) 0x19;

    public final static String eventLog_dataFill =
            "00112233445566778899AABBCCDDEEFF00112233445566778899AABBCC";

    public static EnumMap<PoTransaction.SamSettings, Byte> getSamSettings() {
        /* define the SAM parameters to provide when creating PoTransaction */
        return new EnumMap<PoTransaction.SamSettings, Byte>(PoTransaction.SamSettings.class) {
            {
                put(PoTransaction.SamSettings.SAM_DEFAULT_KIF_PERSO,
                        PoTransaction.DEFAULT_KIF_PERSO);
                put(PoTransaction.SamSettings.SAM_DEFAULT_KIF_LOAD, PoTransaction.DEFAULT_KIF_LOAD);
                put(PoTransaction.SamSettings.SAM_DEFAULT_KIF_DEBIT,
                        PoTransaction.DEFAULT_KIF_DEBIT);
                put(PoTransaction.SamSettings.SAM_DEFAULT_KEY_RECORD_NUMBER,
                        PoTransaction.DEFAULT_KEY_RECORD_NUMER);
            }
        };
    }
}
