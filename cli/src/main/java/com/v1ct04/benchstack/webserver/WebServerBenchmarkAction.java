package com.v1ct04.benchstack.webserver;

import com.google.common.util.concurrent.ListenableFuture;
import com.v1ct04.benchstack.concurrent.MoreFutures;
import com.v1ct04.benchstack.driver.BenchmarkAction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class WebServerBenchmarkAction implements BenchmarkAction {

    private final RestfulHttpClient mHttpClient;
    private final ClientFactory mClientFactory;

    private final Map<Integer, WebServerClient> mClients = new ConcurrentHashMap<>();

    // TODO: Make this configurable
    private Arbitrator<Function<WebServerClient, ListenableFuture<?>>> mArbitrator = new Arbitrator<>();
    {
        mArbitrator
                .addFunction(35, WebServerClient::doReadLite)
                .addFunction(20, WebServerClient::doReadMedium)
                .addFunction(5, WebServerClient::doReadHeavy)

                .addFunction(12, WebServerClient::doUpdateLite)
                .addFunction(5, WebServerClient::doUpdateMedium)
                .addFunction(3, WebServerClient::doUpdateHeavy)

                .addFunction(3, WebServerClient::doInsertLite)
                .addFunction(2, WebServerClient::doInsertHeavy)

                .addFunction(3, WebServerClient::doDeleteLite)
                .addFunction(2, WebServerClient::doDeleteHeavy)

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
        return MoreFutures.consume(
                mClientFactory.create(mHttpClient, workerNum),
                client -> mClients.put(workerNum, client));
    }

    public interface ClientFactory {
        ListenableFuture<? extends WebServerClient> create(RestfulHttpClient client, int workerNum);
    }
}
