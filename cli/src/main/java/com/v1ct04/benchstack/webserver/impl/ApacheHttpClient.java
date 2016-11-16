package com.v1ct04.benchstack.webserver.impl;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.v1ct04.benchstack.webserver.RestfulHttpClient;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;

public class ApacheHttpClient implements RestfulHttpClient, Closeable {

    private final HttpHost mHost;
    private final CloseableHttpAsyncClient mClient;

    public ApacheHttpClient(String host, int port) {
        mHost = new HttpHost(host, port);
        mClient = HttpAsyncClients.createDefault();
        mClient.start();
    }

    @Override
    public void close() throws IOException {
        mClient.close();
    }

    @Override
    public ListenableFuture<JSONObject> doGet(String path) {
        return executeJsonRequest(new HttpGet(mHost.toURI() + path));
    }

    @Override
    public ListenableFuture<JSONObject> doPost(String path, JSONObject body) {
        HttpPost post = new HttpPost(mHost.toURI() + path);
        post.setEntity(new StringEntity(body.toString(), ContentType.APPLICATION_JSON));
        return executeJsonRequest(post);
    }

    private ListenableFuture<JSONObject> executeJsonRequest(HttpRequest request) {
        SettableFuture<JSONObject> future = SettableFuture.create();
        mClient.execute(mHost, request, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse result) {
                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    result.getEntity().writeTo(out);
                    future.set(new JSONObject(out.toString()));
                } catch (Throwable t) {
                    future.setException(t);
                }
            }

            @Override
            public void failed(Exception ex) {
                future.setException(ex);
            }

            @Override
            public void cancelled() {
                future.cancel(false);
            }
        });
        return future;
    }
}
