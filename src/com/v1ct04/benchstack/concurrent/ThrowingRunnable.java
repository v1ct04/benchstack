package com.v1ct04.benchstack.concurrent;

import com.google.common.base.Throwables;

public interface ThrowingRunnable {
    void run() throws Exception;

    static Runnable propagating(ThrowingRunnable r) {
        return () -> runPropagating(r);
    }

    static void runPropagating(ThrowingRunnable r) {
        try {
            r.run();
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }
}
