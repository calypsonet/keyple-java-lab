/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License version 2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.keyple.example.android.nfc;


import org.eclipse.keyple.example.remote.common.ClientNode;
import org.eclipse.keyple.example.remote.wspolling.client_retrofit.WsPollingRetrofitFactory;
import org.eclipse.keyple.plugin.android.nfc.AndroidNfcFragment;
import org.eclipse.keyple.plugin.android.nfc.AndroidNfcPlugin;
import org.eclipse.keyple.plugin.android.nfc.AndroidNfcProtocolSettings;
import org.eclipse.keyple.plugin.android.nfc.AndroidNfcReader;
import org.eclipse.keyple.plugin.remote_se.nse.NativeSeRemoteService;
import org.eclipse.keyple.seproxy.ProxyReader;
import org.eclipse.keyple.seproxy.SeProxyService;
import org.eclipse.keyple.seproxy.exception.KeypleBaseException;
import org.eclipse.keyple.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.seproxy.protocol.SeProtocolSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import android.app.Fragment;
import android.app.FragmentManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;


/**
 * Test the Keyple NFC Plugin Configure the NFC reader Configure the Observability Run test commands
 * when appropriate tag is detected.
 */
public class NFCTestFragment extends Fragment{

    private static final Logger LOG = LoggerFactory.getLogger(NFCTestFragment.class);

    private static final String TAG = NFCTestFragment.class.getSimpleName();
    private static final String TAG_NFC_ANDROID_FRAGMENT =
            "org.eclipse.keyple.plugin.android.nfc.AndroidNfcFragment";

    //client node Id
    static public String sNodeId = "AndroidClientTest1";

    // UI
    private TextView mText;
    private Switch mConnectSwitch;
    private Switch mCreateSwitch;
    private Switch mConnectServer;
    private ProgressBar mSpinner;




    //Keyple Objects
    ProxyReader mNativeReader;
    NativeSeRemoteService mSeRemoteService;

    //Transport Objects
    ClientNode mRseClient;

    public static NFCTestFragment newInstance() {
        return new NFCTestFragment();
    }

    /*
     * Initialize SEProxy with Keyple Android NFC Plugin Add this view to the list of Observer
     * of @{@link ProxyReader}
     *
     * @param savedInstanceState
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createAndroidNfcPlugin();

    }


    /**
     * Initialize UI for NFC Test view
     *
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return
     */
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        // Define UI components
        View view =
                inflater.inflate(R.layout.fragment_nfc_test,
                        container, false);
        mText = view.findViewById(R.id.text);
        mConnectSwitch = view.findViewById(R.id.switchReaderConnect);
        mCreateSwitch = view.findViewById(R.id.switchCreateReader);
        mConnectServer = view.findViewById(R.id.connectServer);
        mSpinner = view.findViewById(R.id.waitingSmartcard);

        //init UI component
        mSpinner.setVisibility(View.GONE);



        //Create Reader Button
        mCreateSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {
                    mNativeReader =  createAndroidNfcReader();
                } else {
                    destroyAndroidNfcReader();
                }
            }
        });

        //Create Reader Button
        mConnectServer.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mSeRemoteService = connect();
                } else {
                    disconnect();
                }
            }
        });

        //Connect Reader Button
        mConnectSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {
                    connectReader(mNativeReader);
                    initWaitCardView();
                } else {
                    disconnectReader(mNativeReader);
                    initTextView();
                }
            }
        });





        initTextView();

        return view;
    }

    private void createAndroidNfcPlugin() {

        // 1 - First initialize SEProxy with Android Plugin
        LOG.info("Initialize SEProxy with Android Plugin");
        SeProxyService seProxyService = SeProxyService.getInstance();
        seProxyService.addPlugin(AndroidNfcPlugin.getInstance());

    }

    private ProxyReader createAndroidNfcReader(){

        // 2 - add NFC Fragment to activity in order to communicate with Android Plugin
        LOG.info("Add Keyple NFC Fragment to activity in order to "
                + "communicate with Android Plugin");
        getFragmentManager().beginTransaction()
                .add(AndroidNfcFragment.newInstance(), TAG_NFC_ANDROID_FRAGMENT).commit();


        try {
            // 3 - Configure Local Reader
            LOG.info("Configure Local Reader");
            ProxyReader localReader = SeProxyService.getInstance().getPlugin("AndroidNFCPlugin").getReader("AndroidNfcReader");

            localReader.setParameter("FLAG_READER_PRESENCE_CHECK_DELAY", "5000");
            localReader.setParameter("FLAG_READER_NO_PLATFORM_SOUNDS", "0");
            localReader.setParameter("FLAG_READER_SKIP_NDEF_CHECK", "0");


            // with this protocol settings we activate the nfc for ISO1443_4 protocol
            ((AndroidNfcReader) localReader).addSeProtocolSetting(
                    new SeProtocolSetting(AndroidNfcProtocolSettings.SETTING_PROTOCOL_ISO14443_4));

            Toast.makeText(getActivity(), "New Reader Created : "+ localReader.getName(), Toast.LENGTH_LONG).show();

            return localReader;

        } catch (KeypleBaseException e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), "An error occurs while creating new reader", Toast.LENGTH_LONG).show();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), "An error occurs while creating new reader", Toast.LENGTH_LONG).show();
        }
        return null;
    }



    private void destroyAndroidNfcReader() {

        //AndroidNfcReader is a singleton, can not be destroyed

        // destroy AndroidNFC fragment
        FragmentManager fm = getFragmentManager();
        Fragment f = fm.findFragmentByTag(TAG_NFC_ANDROID_FRAGMENT);
        if (f != null) {
            fm.beginTransaction().remove(f).commit();
        }

    }

    private NativeSeRemoteService connect(){
        //server configuration
        String keypleUrl = "/keypleDTO";
        String pollingUrl = "/polling";
        Integer port = 8007;
        String bindUrl = "192.168.0.12";
        String protocol = "http://";

        // Setup RSE Client
        WsPollingRetrofitFactory transportFactory = new WsPollingRetrofitFactory(port,pollingUrl,keypleUrl, sNodeId,bindUrl,protocol);
        mRseClient = transportFactory.getClient(false);

        //Setup ClientNode to server
        mSeRemoteService = new NativeSeRemoteService(mRseClient);// ougoing traffic
        mSeRemoteService.bindDtoEndpoint(mRseClient);// incoming traffic
        mRseClient.connect();//connect to server
        Toast.makeText(getActivity(), "RemoteService connected succesfully", Toast.LENGTH_LONG).show();
        return mSeRemoteService;
    }

    private void disconnect(){
        mRseClient.disconnect();
        Toast.makeText(getActivity(), "RemoteService disconnected", Toast.LENGTH_LONG).show();
    }


    private void connectReader(ProxyReader reader){
        // The toggle is enabled
        try{
            LOG.info("Connect local reader to RSE plugin");
            mSeRemoteService.connectReader(sNodeId, reader,  new HashMap<String, Object>());
            Toast.makeText(getActivity(), reader.getName() + " connected succesfully", Toast.LENGTH_LONG).show();
        }catch (KeypleReaderException e){
            Toast.makeText(getActivity(), "An error occurs while connecting "+reader.getName(), Toast.LENGTH_LONG).show();
        }
    }

    private void disconnectReader(ProxyReader reader){
        // The toggle is enabled
        try{
            LOG.info("Connect local reader to RSE plugin");
            mSeRemoteService.disconnectReader(sNodeId, reader);
            Toast.makeText(getActivity(), reader.getName() + " disconnected", Toast.LENGTH_LONG).show();
        }catch (KeypleReaderException e){
            Toast.makeText(getActivity(), "An error occurs while disconnecting "+reader.getName(), Toast.LENGTH_LONG).show();
        }

    }



    /**
     * Revocation of the Activity from @{@link AndroidNfcReader} list of observers
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyAndroidNfcReader();

    }


    private void initWaitCardView(){
        mText.setText("");// reset
        mText.append("\n ---- \n");
        appendColoredText(mText, "Waiting for a smartcard...", Color.BLUE);
        mText.append("\n ---- \n");
        mSpinner.setVisibility(View.VISIBLE);

    }

    private void initTextView() {
        mText.setText("");// reset
        mText.append("\n ---- \n");
        mText.append("\n\n");
        appendColoredText(mText, "Connect Reader, enable polling and slave mode to start demo", Color.BLACK);
        mText.append("\n\n");
        mText.append("\n ---- \n");
        mSpinner.setVisibility(View.GONE);


    }

    private static void appendColoredText(TextView tv, String text, int color) {
        int start = tv.getText().length();
        tv.append(text);
        int end = tv.getText().length();

        Spannable spannableText = (Spannable) tv.getText();
        spannableText.setSpan(new ForegroundColorSpan(color), start, end, 0);
    }



}
