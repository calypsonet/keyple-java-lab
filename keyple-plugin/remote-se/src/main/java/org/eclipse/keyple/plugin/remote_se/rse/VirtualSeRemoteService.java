/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.plugin.remote_se.rse;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.eclipse.keyple.plugin.remote_se.transport.*;
import org.eclipse.keyple.plugin.remote_se.transport.json.JsonParser;
import org.eclipse.keyple.seproxy.ProxyReader;
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

    private static final Logger logger = LoggerFactory.getLogger(VirtualSeRemoteService.class);

    private DtoSender dtoSender;
    private final SeProxyService seProxyService;
    private RsePlugin plugin;
    private VirtualSeSessionManager sessionManager;

    /**
     * Build a new VirtualSeRemoteService,
     * Entry point for incoming DTO in Master
     * Manages RsePlugin lifecycle
     * Manages Master Session
     * Dispatch KeypleDTO
     * @param seProxyService : SeProxyService
     * @param dtoSender : outgoing node to send Dto to Slave
     */
    public VirtualSeRemoteService(SeProxyService seProxyService, DtoSender dtoSender) {
        this.seProxyService = seProxyService;
        this.dtoSender = dtoSender;

        //Instanciate Session Manager
        this.sessionManager = new VirtualSeSessionManager();

        //Instanciate Plugin
        this.plugin = new RsePlugin();
        seProxyService.addPlugin(this.plugin);
    }

    /**
     * Set this service as the Dto Dispatcher in your {@link TransportNode}
     * todo : can't it be the transport node thats set the dispatcher instead?
     * @param node : incoming Dto point
     */
    public void bindDtoEndpoint(TransportNode node) {
        node.setDtoDispatcher(this);
    }

    /**
     * Retrieve the Rse Plugin
     * todo : can't it be the SeProxyService?
     * @return
     */
    public RsePlugin getPlugin() {
        return plugin;
    }

    /**
     * Dispatch incoming transportDTO
     * @param transportDto
     * @return response
     */
    @Override
    public TransportDto onDTO(TransportDto transportDto) {

        KeypleDto keypleDTO = transportDto.getKeypleDTO();
        TransportDto out = null;
        logger.debug("onDTO {}", KeypleDtoHelper.toJson(transportDto.getKeypleDTO()));


        // READER EVENT : SE_INSERTED, SE_REMOVED etc..
        if (keypleDTO.getAction().equals(KeypleDtoHelper.READER_EVENT)) {
            logger.info("**** ACTION - READER_EVENT ****");

            //parse body
            ReaderEvent event =
                    JsonParser.getGson().fromJson(keypleDTO.getBody(), ReaderEvent.class);

            // dispatch reader event
            plugin.onReaderEvent(event, keypleDTO.getSessionId());

            // chain response with a seRequest if needed
            out = isSeRequestToSendBack(transportDto);

        } else if (keypleDTO.getAction().equals(KeypleDtoHelper.READER_CONNECT)) {
            logger.info("**** ACTION - READER_CONNECT ****");

            // parse msg
            String nativeReaderName = keypleDTO.getNativeReaderName();
            String clientNodeId = keypleDTO.getNodeId();

            //create a new session in master
            IReaderSession rseSession  = sessionManager.createSession(nativeReaderName,clientNodeId);

            //DtoSender sends Dto when the session requires to
            ((ReaderSessionImpl) rseSession).addObserver(transportDto.getDtoSender());

            //create a virtual Reader
            String virtualName = plugin.createVirtualReader(nativeReaderName, rseSession);

            // response
            JsonObject respBody = new JsonObject();
            respBody.add("statusCode", new JsonPrimitive(0));
            out = transportDto.nextTransportDTO(new KeypleDto(KeypleDtoHelper.READER_CONNECT,
                    respBody.toString(), false, rseSession.getSessionId(),nativeReaderName,virtualName, clientNodeId));

        } else if (keypleDTO.getAction().equals(KeypleDtoHelper.READER_DISCONNECT)) {
            logger.info("**** ACTION - READER_DISCONNECT ****");

            //JsonObject body = JsonParser.getGson().fromJson(keypleDTO.getBody(), JsonObject.class);
            String nativeReaderName = keypleDTO.getNativeReaderName();
            String nodeId = keypleDTO.getNodeId();

            try {
                plugin.disconnectRemoteReader(nativeReaderName);//todo find by reader + nodeId
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
                reader = getReaderBySessionId(keypleDTO.getSessionId());
                reader.getSession().asyncSetSeResponseSet(seResponseSet);

                // chain response with a seRequest if needed
                out = isSeRequestToSendBack(transportDto);

            } catch (KeypleReaderNotFoundException e) {
                e.printStackTrace();
                out = transportDto.nextTransportDTO(KeypleDtoHelper.ErrorDTO());
            }
        } else {
            if(KeypleDtoHelper.isACK(keypleDTO)){
                logger.info("**** ACK ****");
            }else{
                logger.info("**** ERROR - UNRECOGNIZED ****");
                logger.error("Receive unrecognized message action : {} {} {} {}", keypleDTO.getAction(),
                        keypleDTO.getSessionId(), keypleDTO.getBody(), keypleDTO.isRequest());
            }
            out = transportDto.nextTransportDTO(KeypleDtoHelper.NoResponse());
        }

        logger.debug("onDTO response {}", KeypleDtoHelper.toJson(out.getKeypleDTO()));
        return out;


    }

    /**
     * Enrich a response Dto with a SeRequestSet if required
     * If not, the return is the same
     * @param tdto : response to be sent
     * @return enriched response with a SeRequestSet if required
     */
    private TransportDto isSeRequestToSendBack(TransportDto tdto) {
        TransportDto out = null;
        try {
            // retrieve reader by session
            RseReader rseReader = (RseReader) plugin.getReaderByRemoteName(tdto.getKeypleDTO().getNativeReaderName());

            if ((rseReader.getSession()).hasSeRequestSet()) {

                // send back seRequestSet
                out = tdto.nextTransportDTO(new KeypleDto(KeypleDtoHelper.READER_TRANSMIT,
                        JsonParser.getGson().toJson(
                                (rseReader.getSession()).getSeRequestSet()),
                        true, rseReader.getSession().getSessionId()));
            } else {
                // no response
                out = tdto.nextTransportDTO(KeypleDtoHelper.NoResponse());
            }

        } catch (KeypleReaderNotFoundException e) {
            logger.debug("Reader was not found by session", e);
            out = tdto.nextTransportDTO(KeypleDtoHelper.ErrorDTO());
        }

        return out;
    }


    /**
     * Retrieve reader by a session Id
     * @param sessionId
     * @return RseReader matching the sessionId
     * @throws KeypleReaderNotFoundException
     */
    private RseReader getReaderBySessionId(String sessionId) throws KeypleReaderNotFoundException {
        for (ProxyReader reader : plugin.getReaders()) {

            if (((RseReader)reader).getSession().getSessionId().equals(sessionId)) {
                return (RseReader) reader;
            }
        }
        throw new KeypleReaderNotFoundException(
                "Reader session was not found for session : " + sessionId);
    }

}
