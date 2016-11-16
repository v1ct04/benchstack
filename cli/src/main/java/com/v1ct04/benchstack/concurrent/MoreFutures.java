package com.v1ct04.benchstack.concurrent;

import com.google.common.util.concurrent.*;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class MoreFutures {
    private MoreFutures() {}

    public static <V> ListenableFuture<Void> consume(ListenableFuture<V> future, BiConsumer<? super V, Throwable> consumer) {
        SettableFuture<Void> result = SettableFuture.create();
        Futures.addCallback(future, new FutureCallback<V>() {
            @Override
            public void onSuccess(V v) {
                try {
                    consumer.accept(v, null);
                    result.set(null);
                } catch (Throwable t) {
                    result.setException(t);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                try {
                    consumer.accept(null, t);
                    result.set(null);
                } catch (Throwable ot) {
                    result.setException(ot);
                }
            }
        });
        return result;
    }

    public static <V> ListenableFuture<Void> consume(ListenableFuture<V> future, Consumer<? super V> consumer) {
        return Futures.transform(future, (V r) -> {
            consumer.accept(r);
            return Futures.immediateFuture(null);
        });
    }

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
