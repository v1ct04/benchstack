package com.v1ct04.benchstack.driver;

import com.google.common.util.concurrent.ListenableFuture;

public interface BenchmarkAction {
    ListenableFuture<?> execute(int workerNum) throws Exception;
}
