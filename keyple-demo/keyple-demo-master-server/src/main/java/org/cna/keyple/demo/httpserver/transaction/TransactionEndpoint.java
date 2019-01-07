package org.cna.keyple.demo.httpserver.transaction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.eclipse.keyple.plugin.remotese.transport.json.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class TransactionEndpoint implements HttpHandler {

    private final Logger logger = LoggerFactory.getLogger(TransactionEndpoint.class);

    SaleTransactionManager saleTransactionManager;

    public TransactionEndpoint(SaleTransactionManager saleTransactionManager){
        this.saleTransactionManager = saleTransactionManager ;
    }

    @Override
    public void handle(HttpExchange t) throws IOException {

        logger.trace("Incoming HttpExchange {} ", t.toString());
        logger.trace("Incoming Request {} ", t.getRequestMethod());
        String requestMethod = t.getRequestMethod();

        Map<String, String> params = queryToMap(t.getRequestURI().getQuery());
        String sessionId = params.get("sessionId");


        if (requestMethod.equals("PUT")) {
            URI uri = t.getRequestURI();
            String responseBody = "Path: " + uri.getPath() + "\n";
            Integer responseCode = 200;

            t.getResponseHeaders().add("Content-Type", "application/json");
            t.sendResponseHeaders(responseCode, responseBody.length());
            OutputStream os = t.getResponseBody();
            os.write(responseBody.getBytes());
            os.close();
        }

        if (requestMethod.equals("GET")) {
            String responseBody = JsonParser.getGson().toJson(saleTransactionManager.findAll());

            Integer responseCode = 200;
            t.getResponseHeaders().add("Content-Type", "application/json");
            t.sendResponseHeaders(responseCode, responseBody.length());
            OutputStream os = t.getResponseBody();
            os.write(responseBody.getBytes());
            os.close();
        }


    }

    private String parseBodyToString(InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    private Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<String, String>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            }else{
                result.put(entry[0], "");
            }
        }
        return result;
    }

}
