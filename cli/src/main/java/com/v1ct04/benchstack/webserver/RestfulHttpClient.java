package com.v1ct04.benchstack.webserver;

import com.google.common.util.concurrent.ListenableFuture;
import org.apache.http.NameValuePair;
import org.json.JSONObject;

import java.io.Closeable;

public interface RestfulHttpClient extends Closeable {
    ListenableFuture<JSONObject> doGet(String path, NameValuePair... params);

    ListenableFuture<JSONObject> doPost(String path, JSONObject body);
}
