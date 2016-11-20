package com.v1ct04.benchstack.webserver.impl;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.v1ct04.benchstack.concurrent.BottomlessQueue;
import com.v1ct04.benchstack.concurrent.MoreFutures;
import com.v1ct04.benchstack.webserver.RestfulHttpClient;
import com.v1ct04.benchstack.webserver.WebServerClient;
import com.v1ct04.benchstack.webserver.WebServerResponseException;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PokestackWebServerClient implements WebServerClient {

    public static ListenableFuture<PokestackWebServerClient> asyncCreate(RestfulHttpClient client, int workerNum) {
        JSONObject args = new JSONObject().put("workerNum", workerNum);
        String path = "/api/user/findOrCreate";
        return Futures.transform(
                data(path, client.doPost(path, args)),
                (JSONObject data) -> {
                    assert data != null;
                    return new PokestackWebServerClient(client, data.getString("id"));
                });
    }

    private final Random mRandom = new Random();

    private final RestfulHttpClient mClient;
    private final String mUserId;
    private final JSONObject mUserIdBody;
    private final NearbyElements mNearby;

    private PokestackWebServerClient(RestfulHttpClient client, String userId) {
        mClient = client;

        mUserId = userId;
        mUserIdBody = new JSONObject().put("userId", userId);
        mNearby = new NearbyElements(userId);
    }

    // Benchmark Actions

    @Override
    public ListenableFuture doReadLite() {
        switch (mRandom.nextInt(2)) {
            case 0:
                return doGet("/api/user/" + mUserId);
            case 1:
                // for Read Lite do a cheaper peek instead of polling the objects queue
                return mNearby.stadiums.peekTransform(id -> doGet("/api/stadium/" + id));
            case 2:
                return mNearby.trainers.peekTransform(id -> doGet("/api/trainer/" + id));
        }
        throw new RuntimeException();
    }

    @Override
    public ListenableFuture doReadMedium() {
        switch (mRandom.nextInt(3)) {
            case 0:
                return doGet("/api/nearby/" + mUserId + "/pokestop/closest", makeParam("count", 10));
            case 1:
                return doGet("/api/nearby/" + mUserId + "/pokestop");
            case 2:
                return doGet("/api/nearby/" + mUserId + "/stadium/closest", makeParam("count", 200));
        }
        throw new RuntimeException();
    }

    @Override
    public ListenableFuture doReadHeavy() {
        switch (mRandom.nextInt(4)) {
            case 0:
                return mNearby.pokemons.clear().peek();
            case 1:
                return doGet("/api/user/" + mUserId + "/pokemons");
            case 2:
                return mNearby.stadiums.pollTransform(id -> doGet("/api/stadium/" + id + "/pokemons"));
            case 3:
                return mNearby.trainers.pollTransform(id -> doGet("/api/trainer/" + id + "/pokemons"));
        }
        throw new RuntimeException();
    }

    @Override
    public ListenableFuture doUpdateLite() {
        switch (mRandom.nextInt(2)) {
            case 0:
                return mNearby.pokestops.pollTransform(
                        id -> doPost("/api/pokestop/" + id + "/collect", mUserIdBody));
            case 1:
                return doPost("/api/user/" + mUserId + "/bag/drop", randomBagDropBody());
        }
        throw new RuntimeException();
    }

    @Override
    public ListenableFuture doUpdateMedium() {
        if (mRandom.nextDouble() < 0.1) {
            return doReset();
        } else {
            return doPost("/api/pokestop/improve", userIdCountBody(20));
        }
    }

    @Override
    public ListenableFuture doUpdateHeavy() {
        return doPost("/api/pokemon/levelUp", userIdCountBody(100));
    }

    @Override
    public ListenableFuture doInsertLite() {
        return mNearby.pokestops.pollTransform(id -> doPost("/api/pokestop/" + id + "/lure", userIdCountBody(10)));
    }

    @Override
    public ListenableFuture doInsertHeavy() {
        return mNearby.pokestops.pollTransform(id -> doPost("/api/pokestop/" + id + "/lure", userIdCountBody(200)));
    }

    @Override
    public ListenableFuture doDeleteLite() {
        return doPost("/api/pokemon/nuke", userIdCountBody(10));
    }

    @Override
    public ListenableFuture doDeleteHeavy() {
        return doPost("/api/pokemon/nuke", userIdCountBody(200));
    }

    @Override
    public ListenableFuture doCPULite() {
        return mNearby.pokemons.pollTransform(id -> doPost("/api/pokemon/" + id + "/capture", mUserIdBody));
    }

    @Override
    public ListenableFuture doCPUHeavy() {
        switch (mRandom.nextInt(3)) {
            case 0:
                return mNearby.pokemons.pollTransform(id -> doPost("/api/battle/pokemon/" + id, mUserIdBody));
            case 1:
                return mNearby.stadiums.pollTransform(id -> doPost("/api/battle/stadium/" + id, mUserIdBody));
            case 2:
                return mNearby.trainers.pollTransform(id -> doPost("/api/battle/trainer/" + id, mUserIdBody));
        }
        throw new RuntimeException();
    }

    // Internal Requests

    private ListenableFuture doReset() {
        return MoreFutures.consume(
                doPost("/api/user/" + mUserId + "/move", new JSONObject()),
                r -> mNearby.clear());
    }

    // Helpers

    private ListenableFuture<JSONObject> doPost(String path, JSONObject body) {
        return data(path, mClient.doPost(path, body));
    }

    private ListenableFuture<JSONObject> doGet(String path, NameValuePair... params) {
        return data(path, mClient.doGet(path, params));
    }

    private JSONObject randomBagDropBody() {
        return new JSONObject()
                .put("items", new JSONObject()
                        .put("pokeball", mRandom.nextInt(10))
                        .put("greatball", mRandom.nextInt(4))
                        .put("revive", mRandom.nextInt(5))
                        .put("lure", mRandom.nextInt(5)));
    }

    private JSONObject userIdCountBody(int count) {
        return new JSONObject()
                .put("userId", mUserId)
                .put("count", count);
    }

    private static ListenableFuture<JSONObject> data(String path, ListenableFuture<JSONObject> future) {
        return Futures.transform(future, (JSONObject o) -> {
            assert o != null;
            if (o.getInt("success") == 0) {
                throw new WebServerResponseException(path, o.getString("err"));
            }
            return o.getJSONObject("data");
        });
    }

    private static NameValuePair makeParam(String name, Object value) {
        return new BasicNameValuePair(name, value.toString());
    }

    private class NearbyElements {
        final BottomlessQueue<String> pokemons;
        final BottomlessQueue<String> pokestops;
        final BottomlessQueue<String> stadiums;
        final BottomlessQueue<String> trainers;

        public NearbyElements(String userId) {
            pokemons = nearbyItemsQueue(userId, "pokemon", 30);
            pokestops = nearbyItemsQueue(userId, "pokestop", 20);
            stadiums = nearbyItemsQueue(userId, "stadium", 10);
            trainers = nearbyItemsQueue(userId, "trainer", 5);
        }

        public void clear() {
            pokemons.clear();
            pokestops.clear();
            stadiums.clear();
            trainers.clear();
        }

        private BottomlessQueue<String> nearbyItemsQueue(String userId, String itemType, int refillCount) {
            String path = String.format("/api/nearby/%s/%s/closest", userId, itemType);
            NameValuePair countParam = makeParam("count", refillCount);

            Callable<ListenableFuture<List<String>>> fetcher = () -> {
                return Futures.transform(doGet(path, countParam), (JSONObject data) -> {
                    if (data == null) return Collections.emptyList();
                    return StreamSupport.stream(data.getJSONArray(itemType).spliterator(), false)
                            .map(o -> (JSONObject) o)
                            .map(o -> o.getString("_id"))
                            .collect(Collectors.toList());
                });
            };
            return new BottomlessQueue<>(fetcher);
        }
    }
}
