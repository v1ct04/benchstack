package com.v1ct04.benchstack.webserver.impl;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.v1ct04.benchstack.webserver.WebServerResponseException;
import io.netty.handler.codec.http.HttpHeaders;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.Response;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;

import static org.asynchttpclient.extras.guava.ListenableFutureAdapter.asGuavaFuture;

public class NingHttpClient extends AbstractRestfulHttpClient {

    private final AsyncHttpClient mClient;

    public NingHttpClient(URI baseUri) {
        super(baseUri);
        mClient = new DefaultAsyncHttpClient();
    }

    @Override
    public void close() throws IOException {
        mClient.close();
    }

    @Override
    protected ListenableFuture<JSONObject> doGet(String uri) {
        return toJsonFuture(mClient.prepareGet(uri).execute());
    }

    @Override
    protected ListenableFuture<JSONObject> doPost(String uri, String jsonContent) {
        return toJsonFuture(mClient.preparePost(uri)
                .setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json")
                .setBody(jsonContent)
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
