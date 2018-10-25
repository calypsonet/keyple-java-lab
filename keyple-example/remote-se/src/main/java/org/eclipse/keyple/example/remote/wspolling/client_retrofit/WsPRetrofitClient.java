/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.example.remote.wspolling.client_retrofit;

import okhttp3.OkHttpClient;
import org.eclipse.keyple.example.remote.common.ClientNode;
import org.eclipse.keyple.example.remote.wspolling.WsPTransportDTO;
import org.eclipse.keyple.plugin.remote_se.transport.DtoDispatcher;
import org.eclipse.keyple.plugin.remote_se.transport.KeypleDto;
import org.eclipse.keyple.plugin.remote_se.transport.KeypleDtoHelper;
import org.eclipse.keyple.plugin.remote_se.transport.TransportDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * Rest client, polls server, based on retrofit and callbacks
 */
public class WsPRetrofitClient implements ClientNode {


    private static final Logger logger = LoggerFactory.getLogger(WsPRetrofitClient.class);

    private String baseUrl;
    private String nodeId;

    private DtoDispatcher dtoDispatcher;

    public WsPRetrofitClient(String baseUrl, String keypleDtoEndpoint, String pollingEndpoint, String nodeId) {
        this.baseUrl = baseUrl;
        this.nodeId = nodeId;
    }


    /**
     * recursive polling method based on retrofit callbacks
     * @param nodeId
     */
    public void startPollingWorker(final String nodeId) {

        logger.trace("Polling clientNodeId {}", nodeId);

        Call<KeypleDto> call = getRseAPIClient(baseUrl).getPolling(nodeId);

        call.enqueue(new Callback<KeypleDto>() {
            @Override
            public void onResponse(Call<KeypleDto> call, Response<KeypleDto> response) {
                int statusCode = response.code();
                logger.trace("Polling for clientNodeId {} receive a httpResponse http code {}", nodeId,
                        statusCode);
                processHttpResponseDTO(response);
                startPollingWorker(nodeId);//recursive call to restart polling
            }

            @Override
            public void onFailure(Call<KeypleDto> call, Throwable t) {
                // Log error here since request failed
                logger.debug("polling ends, start it over, error : " + t.getMessage());
                startPollingWorker(nodeId);//recursive call to restart polling
            }
        });



    }


    private void processHttpResponseDTO(Response<KeypleDto> response) {

        KeypleDto responseDTO = response.body();

        if(!KeypleDtoHelper.isNoResponse(responseDTO)){
            TransportDto transportDto = new WsPTransportDTO(responseDTO, this);
            // connection
            final TransportDto sendback = this.dtoDispatcher.onDTO(transportDto);

            // if sendBack is not a noresponse (can be a keyple request or keyple response)
            if (!KeypleDtoHelper.isNoResponse(sendback.getKeypleDTO())) {
                // send the keyple object in a new thread to avoid blocking the polling

             sendDTO(sendback);

            }
        }


    }


    @Override
    public void sendDTO(TransportDto tdto) {
        KeypleDto ktdo = tdto.getKeypleDTO();
        logger.debug("Ws Client send DTO {}", KeypleDtoHelper.toJson(ktdo));

        if (!KeypleDtoHelper.isNoResponse(tdto.getKeypleDTO())) {

            Call<KeypleDto> call = getRseAPIClient(baseUrl).postDto(ktdo);
            call.enqueue(new Callback<KeypleDto>() {
                @Override
                public void onResponse(Call<KeypleDto> call, Response<KeypleDto> response) {
                    int statusCode = response.code();
                    logger.trace("Polling for clientNodeId {} receive a httpResponse http code {}", nodeId,
                            statusCode);
                    processHttpResponseDTO(response);
                }

                @Override
                public void onFailure(Call<KeypleDto> call, Throwable t) {
                    // Log error here since request failed
                    logger.debug("polling ends, start it over" + t.getMessage());
                    startPollingWorker(nodeId);
                }
            });

        }
    }

    @Override
    public void sendDTO(KeypleDto message) {
        sendDTO(new WsPTransportDTO(message, null));
    }

    @Override
    public String getNodeId() {
        return this.nodeId;
    }

    @Override
    public void update(KeypleDto event) {
        this.sendDTO(event);
    }


    /*
     * TransportNode
     */
    @Override
    public void setDtoDispatcher(DtoDispatcher dtoDispatcher) {
        this.dtoDispatcher = dtoDispatcher;
    }


    @Override
    public void connect() {
        this.startPollingWorker(nodeId);
    }




    private org.eclipse.keyple.example.remote.wspolling.client_retrofit.RseAPI getRseAPIClient(String baseUrl){

        final OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit.create(org.eclipse.keyple.example.remote.wspolling.client_retrofit.RseAPI.class);
    }

}
