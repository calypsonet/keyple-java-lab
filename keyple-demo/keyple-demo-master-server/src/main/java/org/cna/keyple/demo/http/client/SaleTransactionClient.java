package org.cna.keyple.demo.http.client;

import org.cna.keyple.demo.httpserver.transaction.SaleTransaction;
import org.eclipse.keyple.plugin.remotese.transport.KeypleDto;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface SaleTransactionClient {


    @Headers({"Accept: application/json", "Content-Type: application/json; charset=UTF-8"})
    @PUT("saleTransaction")
    Call<String> update(@Query("transactionId") String transactionId, @Query("status") Integer status);


    @Headers({"Accept: application/json", "Content-Type: application/json; charset=UTF-8"})
    @GET("saleTransaction")
    Call<List<SaleTransaction>> findAll();


}
