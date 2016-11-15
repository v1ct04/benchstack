package com.v1ct04.benchstack.driver;

public interface BenchmarkAction {
    void execute(int workerNum) throws Exception;
}
