package org.cna.keyple.demo.httpserver;

import okhttp3.OkHttpClient;
import org.cna.keyple.demo.http.client.SaleTransactionClient;
import org.cna.keyple.demo.http.client.SaleTransactionClientFactory;
import org.cna.keyple.demo.httpserver.transaction.SaleTransaction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;



public class SaleTransactionTest {

    SaleTransactionClient saleTransactionClient;
    String baseUrl = "http://127.0.0.1:8002";

    @Before
    public void seTup() {
        saleTransactionClient = SaleTransactionClientFactory.getRetrofitClient(baseUrl);

    }



    @Test
    public void testGetAll(){

        Call<List<SaleTransaction>> transactions = saleTransactionClient.findAll();

        try {
            Response<List<SaleTransaction>> res = transactions.execute();
            Assert.assertNull(res);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }






}
