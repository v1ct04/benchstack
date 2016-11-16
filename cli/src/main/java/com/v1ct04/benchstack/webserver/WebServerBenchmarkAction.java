package com.v1ct04.benchstack.webserver;

import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.v1ct04.benchstack.driver.Arbitrator;
import com.v1ct04.benchstack.driver.BenchmarkAction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebServerBenchmarkAction implements BenchmarkAction {

    private final RestfulHttpClient mHttpClient;
    private final ClientFactory mClientFactory;

    private final Map<Integer, WebServerClient> mClients = new ConcurrentHashMap<>();

    // TODO: Make this configurable
    private Arbitrator<AsyncFunction<WebServerClient, ?>> mArbitrator = new Arbitrator<>();
    {
        mArbitrator
                .addFunction(35, WebServerClient::doReadLite)
                .addFunction(20, WebServerClient::doReadMedium)
                .addFunction(5, WebServerClient::doReadHeavy)
                .addFunction(15, WebServerClient::doUpdateLite)
                .addFunction(5, WebServerClient::doUpdateMedium)
                .addFunction(3, WebServerClient::doUpdateHeavy)
                .addFunction(3, WebServerClient::doInsertLite)
                .addFunction(2, WebServerClient::doInsertHeavy)
                .addFunction(2, WebServerClient::doDelete)
                .addFunction(6, WebServerClient::doCPULite)
                .addFunction(4, WebServerClient::doCPUHeavy);
    }

    public WebServerBenchmarkAction(RestfulHttpClient httpClient,
                                    ClientFactory clientFactory) {
        mHttpClient = httpClient;
        mClientFactory = clientFactory;
    }

    @Override
    public ListenableFuture<?> execute(int workerNum) throws Exception {
        if (!mClients.containsKey(workerNum)) {
            return createClient(workerNum);
        }
        return mArbitrator.arbitrate().apply(mClients.get(workerNum));
    }

    private ListenableFuture<?> createClient(int workerNum) {
        return Futures.transform(mClientFactory.create(mHttpClient, workerNum),
                (WebServerClient client) -> {
                    mClients.put(workerNum, client);
                    return Futures.immediateFuture(null);
                });
    }

    public interface ClientFactory {
        ListenableFuture<? extends WebServerClient> create(RestfulHttpClient client, int workerNum);
    }
}
