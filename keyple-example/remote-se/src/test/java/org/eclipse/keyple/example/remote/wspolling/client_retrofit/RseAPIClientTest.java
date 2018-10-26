package org.eclipse.keyple.example.remote.wspolling.client_retrofit;

import junit.framework.Assert;

import org.eclipse.keyple.plugin.remote_se.transport.KeypleDto;
import org.eclipse.keyple.plugin.remote_se.transport.KeypleDtoHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;

import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
public class RseAPIClientTest {


    final String BASE_URL = "http://localhost:8081/";


    @Before
    public void seTup(){

        //start server


    }

    /**
     * Polling should failed after a timeout is raised on server
     * @throws IOException
     */
    @Test(expected = IOException.class)
    public void testPolling() throws IOException {
        RseAPI rseClient = WsPRetrofitClient.getRseAPIClient(BASE_URL);
        Response<KeypleDto> kdto =  rseClient.getPolling("clientNodeId").execute();

    }

    /**
     * Send a valid READER_CONNECT dto
     * @throws IOException
     */
    @Test
    public void testPostDto() throws IOException {

        KeypleDto dtoConnect = new KeypleDto(KeypleDtoHelper.READER_CONNECT, "{nativeReaderName:test,isAsync:true, clientNodeId:testnode1}", true);

        RseAPI rseClient = WsPRetrofitClient.getRseAPIClient(BASE_URL);
        Response<KeypleDto> resp = rseClient.postDto(dtoConnect).execute();

        Assert.assertEquals(200, resp.code());
        Assert.assertEquals(KeypleDtoHelper.READER_CONNECT, resp.body().getAction());
        Assert.assertFalse(resp.body().isRequest());

    }

}
