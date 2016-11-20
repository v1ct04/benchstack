package com.v1ct04.benchstack.concurrent;

import com.google.common.util.concurrent.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class MoreFutures {
    private MoreFutures() {}

    /**
     * Consumes the given future, guaranteeing that the resulting future will be
     * created and provided to {@code preConsumer} before this function returns
     * and before the actual result {@code consumer} is executed. The resulting
     * future is also returned for convenience.
     */
    public static <V> ListenableFuture<V> lateConsume(ListenableFuture<V> future,
                                                      Consumer<ListenableFuture<V>> preConsumer,
                                                      BiConsumer<? super V, Throwable> consumer) {
        CancelListenableFuture<V> result = new CancelListenableFuture<>(future::cancel);
        preConsumer.accept(result);

        Futures.addCallback(future, new FutureCallback<V>() {
            @Override
            public void onSuccess(V v) {
                try {
                    consumer.accept(v, null);
                    result.set(v);
                } catch (Throwable t) {
                    result.setException(t);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                try {
                    consumer.accept(null, t);
                } finally {
                    result.setException(t);
                }
            }
        });
        return result;
    }

    public static <V> ListenableFuture<V> consume(ListenableFuture<V> future,
                                                  BiConsumer<? super V, Throwable> consumer) {
        return lateConsume(future, f -> {}, consumer);
    }

    public static <V> ListenableFuture<V> consume(ListenableFuture<V> future, Consumer<? super V> consumer) {
        return consume(future, (r, t) -> {if (t == null) consumer.accept(r);});
    }

    public static <V> ListenableFuture<V> setWhileOngoing(ListenableFuture<V> future,
                                                          AtomicReference<ListenableFuture<V>> reference) {
        return lateConsume(future, reference::set, (r, e) -> reference.set(null));
    }

    public static <V> ListenableScheduledFuture<V> dereference(ListenableScheduledFuture<? extends ListenableFuture<? extends V>> future) {
        return new ForwardingListenableScheduledFuture<>(future);
    }

    public static ListenableFuture<Boolean> toSuccessFuture(ListenableFuture<?> future) {
        CancelListenableFuture<Boolean> result = new CancelListenableFuture<>(future::cancel);
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

    public static <V> ListenableFuture<V> resultOf(Callable<ListenableFuture<V>> callable) {
        try {
            return callable.call();
        } catch (Throwable t) {
            return Futures.immediateFailedFuture(t);
        }
    }

    public static ListenableFuture<Void> execAsync(Runnable command) {
        return execAsync(command, Thread::new);
    }

    public static <V> ListenableFuture<V> execAsync(Callable<V> callable) {
        return execAsync(callable, Thread::new);
    }

    public static ListenableFuture<Void> execAsync(Runnable command, ThreadFactory factory) {
        return execAsync(() -> {command.run(); return null;}, factory);
    }

    public static <V> ListenableFuture<V> execAsync(Callable<V> callable, ThreadFactory factory) {
        CancelListenableFuture<V> future = new CancelListenableFuture<>();
        Thread thread = factory.newThread(() -> {
            try {
                future.set(callable.call());
            } catch (Throwable t) {
                future.setException(t);
            }
        });
        future.onCancel = interrupt -> {if (interrupt) thread.interrupt();};
        thread.start();
        return future;
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

    private static class CancelListenableFuture<V> extends AbstractFuture<V> {

        public volatile OnCancelListener onCancel;

        public CancelListenableFuture() {this(i -> {});}

        public CancelListenableFuture(OnCancelListener l) {onCancel = l;}

        @Override
        protected boolean set(V value) {
            return super.set(value);
        }

        @Override
        protected boolean setException(Throwable throwable) {
            return super.setException(throwable);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (super.cancel(mayInterruptIfRunning)) {
                onCancel.onCancel(mayInterruptIfRunning);
                return true;
            }
            return false;
        }

        public interface OnCancelListener {
            void onCancel(boolean mayInterruptIfRunning);
        }
    }
}
