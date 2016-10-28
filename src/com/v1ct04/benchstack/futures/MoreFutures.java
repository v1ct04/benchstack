package com.v1ct04.benchstack.futures;

import com.google.common.util.concurrent.ForwardingListenableFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableScheduledFuture;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public abstract class MoreFutures {
    private MoreFutures() {}

    public static <V> ListenableScheduledFuture<V> dereference(ListenableScheduledFuture<? extends ListenableFuture<? extends V>> future) {
        return new ForwardingListenableScheduledFuture<>(future);
    }

    private static class ForwardingListenableScheduledFuture<V>
            extends ForwardingListenableFuture.SimpleForwardingListenableFuture<V>
            implements ListenableScheduledFuture<V> {
        private final Delayed mScheduled;

        ForwardingListenableScheduledFuture(ListenableScheduledFuture<? extends ListenableFuture<? extends V>> future) {
            super(Futures.dereference(future));
            mScheduled = future;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return mScheduled.getDelay(unit);
        }

        @Override
        public int compareTo(Delayed o) {
            return mScheduled.compareTo(o);
        }
    }
}
