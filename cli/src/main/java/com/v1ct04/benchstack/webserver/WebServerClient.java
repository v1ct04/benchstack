package com.v1ct04.benchstack.webserver;

import com.google.common.util.concurrent.ListenableFuture;

public interface WebServerClient {
    ListenableFuture<?> doReadLite();

    ListenableFuture<?> doReadMedium();

    ListenableFuture<?> doReadHeavy();

    ListenableFuture<?> doUpdateLite();

    ListenableFuture<?> doUpdateMedium();

    ListenableFuture<?> doUpdateHeavy();

    ListenableFuture<?> doInsertLite();

    ListenableFuture<?> doInsertHeavy();

    ListenableFuture<?> doDeleteLite();

    ListenableFuture<?> doDeleteHeavy();

    ListenableFuture<?> doCPULite();

    ListenableFuture<?> doCPUHeavy();
}
