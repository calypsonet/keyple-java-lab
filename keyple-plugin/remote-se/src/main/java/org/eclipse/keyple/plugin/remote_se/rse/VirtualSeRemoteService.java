/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.plugin.remote_se.rse;

import java.util.SortedSet;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.eclipse.keyple.plugin.remote_se.transport.*;
import org.eclipse.keyple.plugin.remote_se.transport.json.JsonParser;
import org.eclipse.keyple.seproxy.ReaderPlugin;
import org.eclipse.keyple.seproxy.SeProxyService;
import org.eclipse.keyple.seproxy.SeResponseSet;
import org.eclipse.keyple.seproxy.event.ReaderEvent;
import org.eclipse.keyple.seproxy.exception.KeypleReaderNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service to setDtoSender a RSE Plugin to a Transport Node
 */
public class VirtualSeRemoteService implements DtoDispatcher{

    private static final Logger logger = LoggerFactory.getLogger(RsePlugin.class);

    private DtoSender dtoSender;
    private final SeProxyService seProxyService;
    private RsePlugin plugin;
    private RseSessionManager sessionManager;

    public VirtualSeRemoteService(SeProxyService seProxyService, DtoSender dtoSender) {
        this.seProxyService = seProxyService;
        this.dtoSender = dtoSender;

        //Instanciate Session Manager
        this.sessionManager = new RseSessionManager();

        //Instanciate Plugin
        this.plugin = new RsePlugin();
        seProxyService.addPlugin(this.plugin);
    }

    /*
    */
    public void bindDtoEndpoint(TransportNode node) {
        node.setDtoDispatcher(this);
    }

    public RsePlugin getPlugin() {
        return plugin;
    }


    // called by node transport
    @Override
    public TransportDto onDTO(TransportDto transportDto) {

        KeypleDto keypleDTO = transportDto.getKeypleDTO();
        TransportDto out = null;
        logger.debug("onDTO {}", KeypleDtoHelper.toJson(transportDto.getKeypleDTO()));


        // if (msg.getHash()!=null && !KeypleDtoHelper.verifyHash(msg, msg.getHash())) {
        // return exception, msg is signed but has is invalid
        // }

        // READER EVENT : SE_INSERTED, SE_REMOVED etc..
        if (keypleDTO.getAction().equals(KeypleDtoHelper.READER_EVENT)) {
            logger.info("**** ACTION - READER_EVENT ****");

            ReaderEvent event =
                    JsonParser.getGson().fromJson(keypleDTO.getBody(), ReaderEvent.class);

            // dispatch reader event
            plugin.onReaderEvent(event, keypleDTO.getSessionId());

            // chain response with a seRequest if needed
            out = plugin.isSeRequestToSendBack(transportDto);

        } else if (keypleDTO.getAction().equals(KeypleDtoHelper.READER_CONNECT)) {
            logger.info("**** ACTION - READER_CONNECT ****");

            // parse msg
            JsonObject body = JsonParser.getGson().fromJson(keypleDTO.getBody(), JsonObject.class);
            String readerName = body.get("nativeReaderName").getAsString();
            Boolean isAsync = body.get("isAsync").getAsBoolean();
            String nodeId = body.get("nodeId").getAsString();


            String sessionId = sessionManager.generateSessionId(readerName, nodeId);
            IReaderSession rseSession;// reader session

            if (!isAsync) {
                // todo
                logger.error("Rse Plugin supports only Async session");
                throw new IllegalArgumentException("Rse Plugin needs a Async Session to work");
            } else {
                // rseSession = new ReaderAsyncSessionImpl(sessionId, message.getDtoSender());
                rseSession = new ReaderAsyncSessionImpl(sessionId);
                // add the web socket node as an observer for the session as the session will send
                // KeypleDto
                ((ReaderAsyncSessionImpl) rseSession).addObserver(transportDto.getDtoSender());

                plugin.connectRemoteReader(readerName, rseSession);
            }


            // response
            JsonObject respBody = new JsonObject();
            respBody.add("statusCode", new JsonPrimitive(0));
            respBody.add("nativeReaderName", new JsonPrimitive(readerName));
            out = transportDto.nextTransportDTO(new KeypleDto(KeypleDtoHelper.READER_CONNECT,
                    respBody.toString(), false, sessionId));

        } else if (keypleDTO.getAction().equals(KeypleDtoHelper.READER_DISCONNECT)) {
            logger.info("**** ACTION - READER_DISCONNECT ****");

            JsonObject body = JsonParser.getGson().fromJson(keypleDTO.getBody(), JsonObject.class);
            String readerName = body.get("nativeReaderName").getAsString();
            String nodeId = body.get("nodeId").getAsString();

            try {
                plugin.disconnectRemoteReader(readerName);//todo find by reader + nodeId
                out = transportDto.nextTransportDTO(KeypleDtoHelper.ACK());

            } catch (KeypleReaderNotFoundException e) {
                e.printStackTrace();
                out = transportDto.nextTransportDTO(KeypleDtoHelper.ErrorDTO());
            }


        } else if (keypleDTO.getAction().equals(KeypleDtoHelper.READER_TRANSMIT)
                && !keypleDTO.isRequest()) {
            logger.info("**** RESPONSE - READER_TRANSMIT ****");

            // parse msg
            SeResponseSet seResponseSet =
                    JsonParser.getGson().fromJson(keypleDTO.getBody(), SeResponseSet.class);
            logger.debug("Receive responseSet from transmit {}", seResponseSet);
            RseReader reader = null;
            try {
                reader = plugin.getReaderBySessionId(keypleDTO.getSessionId());
                ((IReaderAsyncSession) reader.getSession()).asyncSetSeResponseSet(seResponseSet);

                // chain response with a seRequest if needed
                out = plugin.isSeRequestToSendBack(transportDto);

            } catch (KeypleReaderNotFoundException e) {
                e.printStackTrace();
                out = transportDto.nextTransportDTO(KeypleDtoHelper.ErrorDTO());
            }
        } else {
            logger.info("**** ERROR - UNRECOGNIZED ****");
            logger.error("Receive unrecognized message action : {} {} {} {}", keypleDTO.getAction(),
                    keypleDTO.getSessionId(), keypleDTO.getBody(), keypleDTO.isRequest());
            out = transportDto.nextTransportDTO(KeypleDtoHelper.NoResponse());
        }

        logger.debug("onDTO response {}", KeypleDtoHelper.toJson(out.getKeypleDTO()));
        return out;


    }


}
