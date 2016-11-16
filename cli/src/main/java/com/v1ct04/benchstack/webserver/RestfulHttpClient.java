package com.v1ct04.benchstack.webserver;

import com.google.common.util.concurrent.ListenableFuture;
import org.json.JSONObject;

public interface RestfulHttpClient {
    ListenableFuture<JSONObject> doGet(String path);

    ListenableFuture<JSONObject> doPost(String path, JSONObject body);
}
