package com.v1ct04.benchstack.webserver.impl;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.v1ct04.benchstack.webserver.RestfulHttpClient;
import com.v1ct04.benchstack.webserver.WebServerResponseException;
import io.netty.handler.codec.http.HttpHeaders;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.Response;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import static org.asynchttpclient.extras.guava.ListenableFutureAdapter.asGuavaFuture;

public class NingHttpClient implements RestfulHttpClient {

    private static final String BASE_URL = "http://localhost:3000";

    private final AsyncHttpClient mClient;

    public NingHttpClient() {
        mClient = new DefaultAsyncHttpClient();
    }

    @Override
    public void close() throws IOException {
        mClient.close();
    }

    @Override
    public ListenableFuture<JSONObject> doGet(String path) {
        return toJsonFuture(mClient.prepareGet(BASE_URL + path).execute());
    }

    @Override
    public ListenableFuture<JSONObject> doPost(String path, JSONObject body) {
        return toJsonFuture(mClient.preparePost(BASE_URL + path)
                .setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json")
                .setBody(body.toString())
                .execute());
    }

    private static ListenableFuture<JSONObject> toJsonFuture(
            org.asynchttpclient.ListenableFuture<Response> future) {
        return Futures.transform(asGuavaFuture(future),
                (Response r) -> {
                    assert r != null;
                    try {
                        return new JSONObject(r.getResponseBody());
                    } catch (JSONException ex) {
                        throw new WebServerResponseException(r.getUri().getPath(),
                                "Invalid JSON: " + r.getResponseBody(), ex);
                    }
                });
    }
}
