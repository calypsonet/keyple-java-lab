package org.eclipse.keyple.example.remote.wspolling.client_retrofit;


import org.eclipse.keyple.plugin.remote_se.transport.KeypleDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface RseAPI {

    @Headers({
            "Accept: application/json",
            "Content-Type: application/json; charset=UTF-8"
    })
    @GET("polling")
    Call<KeypleDto> getPolling(@Query("clientNodeId") String nodeId);

    @Headers({
            "Accept: application/json",
            "Content-Type: application/json; charset=UTF-8"
    })
    @POST("keypleDTO")
    Call<KeypleDto> postDto(@Body KeypleDto keypleDto);

}
