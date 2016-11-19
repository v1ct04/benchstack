package com.v1ct04.benchstack.concurrent;

import com.google.common.util.concurrent.*;

import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class MoreFutures {
    private MoreFutures() {}

    public static <V> ListenableFuture<V> consume(ListenableFuture<V> future, BiConsumer<? super V, Throwable> consumer) {
        Futures.addCallback(future, new FutureCallback<V>() {
            @Override
            public void onSuccess(V v) {
                consumer.accept(v, null);
            }

            @Override
            public void onFailure(Throwable t) {
                consumer.accept(null, t);
            }
        });
        return future;
    }

    public static <V> ListenableFuture<V> consume(ListenableFuture<V> future, Consumer<? super V> consumer) {
        Futures.addCallback(future, new FutureCallback<V>() {
            @Override
            public void onSuccess(V result) {
                consumer.accept(result);
            }

            @Override
            public void onFailure(Throwable t) {}
        });
        return future;
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

    public static ListenableFuture<Void> execAsync(ThrowingRunnable command) {
        return execAsync(command, Thread::new);
    }

    public static <V> ListenableFuture<V> execAsync(Callable<V> callable) {
        return execAsync(callable, Thread::new);
    }

    public static ListenableFuture<Void> execAsync(ThrowingRunnable command, ThreadFactory factory) {
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
