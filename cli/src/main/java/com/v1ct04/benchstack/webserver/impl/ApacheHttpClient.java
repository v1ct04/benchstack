package com.v1ct04.benchstack.webserver.impl;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.Future;

public class ApacheHttpClient extends AbstractRestfulHttpClient {

    private final CloseableHttpAsyncClient mClient;

    public ApacheHttpClient(URI baseUri) {
        super(baseUri);
        mClient = HttpAsyncClients.createDefault();
        mClient.start();
    }

    @Override
    public void close() throws IOException {
        mClient.close();
    }

    @Override
    public ListenableFuture<JSONObject> doGet(String uri) {
        return executeJsonRequest(new HttpGet(uri));
    }

    @Override
    protected ListenableFuture<JSONObject> doPost(String uri, String jsonContent) {
        HttpPost post = new HttpPost(uri);
        post.setEntity(new StringEntity(jsonContent, ContentType.APPLICATION_JSON));
        return executeJsonRequest(post);
    }

    private ListenableFuture<JSONObject> executeJsonRequest(HttpUriRequest request) {
        ApacheFutureAdapter<HttpResponse> future = new ApacheFutureAdapter<>();
        future.delegate = mClient.execute(request, future.makeCallback());
        return Futures.transform(future, (HttpResponse result) -> {
            InputStream content = result.getEntity().getContent();
            JSONTokener tokener = new JSONTokener(content);
            return Futures.immediateFuture(new JSONObject(tokener));
        });
    }

    private class ApacheFutureAdapter<Type> extends AbstractFuture<Type> {
        Future<Type> delegate;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning) && super.cancel(mayInterruptIfRunning);
        }

        FutureCallback<Type> makeCallback() {
            return new FutureCallback<Type>() {
                @Override
                public void completed(Type result) {
                    set(result);
                }

                @Override
                public void failed(Exception ex) {
                    setException(ex);
                }

                @Override
                public void cancelled() {
                    ApacheFutureAdapter.super.cancel(true);
                }
            };
        }
    }
}
