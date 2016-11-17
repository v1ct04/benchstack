package com.v1ct04.benchstack.webserver.impl;

import com.google.common.util.concurrent.ListenableFuture;
import com.v1ct04.benchstack.webserver.RestfulHttpClient;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONObject;

import java.net.URI;

public abstract class AbstractRestfulHttpClient implements RestfulHttpClient {

    private final URI mBaseUri;

    protected AbstractRestfulHttpClient(URI baseUri) {
        mBaseUri = baseUri;
    }

    @Override
    public final ListenableFuture<JSONObject> doGet(String path, NameValuePair... params) {
        return doGet(new URIBuilder(mBaseUri).setPath(path).setParameters(params).toString());
    }

    @Override
    public final ListenableFuture<JSONObject> doPost(String path, JSONObject body) {
        return doPost(new URIBuilder(mBaseUri).setPath(path).toString(), body.toString());
    }

    protected abstract ListenableFuture<JSONObject> doGet(String uri);
    protected abstract ListenableFuture<JSONObject> doPost(String uri, String jsonContent);
}
