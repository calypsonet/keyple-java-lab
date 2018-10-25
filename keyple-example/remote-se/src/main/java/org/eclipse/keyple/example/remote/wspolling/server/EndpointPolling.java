/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.example.remote.wspolling.server;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import org.eclipse.keyple.plugin.remote_se.transport.*;
import org.eclipse.keyple.plugin.remote_se.transport.TransportDto;
import org.eclipse.keyple.plugin.remote_se.transport.TransportNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Endpoint for polling, used to send keypleDto to polling clients
 */
public class EndpointPolling implements HttpHandler, TransportNode {


    private final Logger logger = LoggerFactory.getLogger(EndpointPolling.class);

    DtoDispatcher dtoDispatcher;
    String nodeId;
    private Queue<HttpExchange> requestQueue;

    public EndpointPolling(Queue requestQueue, String nodeId) {
        this.nodeId = nodeId;
        this.requestQueue = requestQueue;
    }

    @Override
    public String getNodeId() {
        return nodeId;
    }

    @Override
    public void handle(HttpExchange t) throws IOException {

        logger.trace("Incoming HttpExchange {} ", t.toString());
        logger.trace("Incoming Request {} ", t.getRequestMethod());
        String requestMethod = t.getRequestMethod();

        if (requestMethod.equals("GET")) {

            // hold response until we got a response or timeout
            Map<String, String> params = queryToMap(t.getRequestURI().getQuery());
            String nodeId = params.get("clientNodeId");
            // logger.trace("param clientNodeId=" + params.get("clientNodeId"));

            logger.trace(
                    "Receive a polling request {} for clientNodeId {}, add it to the queue, queue size before adding {}",
                    t.toString(), nodeId, requestQueue.size());

            // set httpExchange in queue
            requestQueue.add(t);

            // setHttpResponse(t, KeypleDtoHelper.ACK());

        }
    }

    /*
     * TransportNode
     */
    @Override
    public void setDtoDispatcher(DtoDispatcher receiver) {
        this.dtoDispatcher = receiver;
    }


    @Override
    public void sendDTO(TransportDto message) {
        logger.warn("Send DTO with transportmessage {}", message);
        this.sendDTO(message.getKeypleDTO());
    }

    @Override
    public void sendDTO(KeypleDto message) {
        logger.info("Using polling to send keypleDTO {}", KeypleDtoHelper.toJson(message));

        synchronized (requestQueue) {
            logger.debug("Polling Queue size {}", requestQueue.size());

            if (requestQueue.size() == 0) {
                logger.warn("Too bad request Queue is empty, impossible to send DTO");
                throw new IllegalStateException("Request Queue is empty, impossible to send DTO");
            } else {

                HttpExchange t = requestQueue.poll();

                try {
                    logger.debug("Found a wainting HttpExchange {}", t.toString());
                    setHttpResponse(t, message);
                } catch (IOException e) {
                    e.printStackTrace();
                    logger.error("Response to polling has failed");
                }
            }
        }
    }



    @Override
    public void update(KeypleDto event) {
        logger.info("Send DTO from update {}", event.getAction());
        this.sendDTO(event);
    }


    private Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<String, String>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }

    private void setHttpResponse(HttpExchange t, KeypleDto resp) throws IOException {
        if (!resp.getAction().isEmpty()) {
            String responseBody = KeypleDtoHelper.toJson(resp);
            Integer responseCode = 200;
            t.getResponseHeaders().add("Content-Type", "application/json");
            t.sendResponseHeaders(responseCode, responseBody.length());
            OutputStream os = t.getResponseBody();
            os.write(responseBody.getBytes());
            os.close();
            logger.debug("Outcoming Response Code {} ", responseCode);
            logger.debug("Outcoming Response Body {} ", responseBody);


        } else {
            String responseBody = "{}";
            Integer responseCode = 200;
            t.getResponseHeaders().add("Content-Type", "application/json");
            t.sendResponseHeaders(responseCode, responseBody.length());
            OutputStream os = t.getResponseBody();
            os.write(responseBody.getBytes());
            os.close();
        }
    }
}
