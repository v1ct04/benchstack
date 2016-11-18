package com.v1ct04.benchstack.concurrent;

import com.google.common.util.concurrent.*;

import java.util.concurrent.*;
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

    public static <V> V onlyGet(Future<V> future) throws CancellationException, InterruptedException, ExecutionException {
        try {
            return future.get();
        } catch (InterruptedException e) {
            future.cancel(true);
            throw e;
        }
    }

    public static boolean awaitTermination(Future<?> future) throws InterruptedException {
        try {
            future.get();
            return true;
        } catch (ExecutionException | CancellationException e) {
            return false;
        }
    }

    public static boolean awaitTermination(Future<?> future, long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        try {
            future.get(timeout, unit);
            return true;
        } catch (ExecutionException | CancellationException e) {
            return false;
        }
    }

    public static ListenableFuture<Boolean> toSuccessFuture(ListenableFuture<?> future) {
        SettableFuture<Boolean> result = SettableFuture.create();
        Futures.addCallback(future, new FutureCallback<Object>() {
            @Override
            public void onSuccess(Object o) {
                result.set(true);
            }

            @Override
            public void onFailure(Throwable t) {
                result.set(false);
            }
        });
        return result;
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
