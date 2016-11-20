package com.v1ct04.benchstack.concurrent;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class SingleAsyncPerformer<V> {

    public static <V> Builder<V> doing(Callable<ListenableFuture<V>> performer) {
        return new Builder<>(performer);
    }

    private final Callable<ListenableFuture<V>> mOperation;
    private final BooleanSupplier mCondition;
    private final Consumer<V> mConsumer;

    private final AtomicReference<ListenableFuture<V>> mCurrentOperation = new AtomicReference<>();

    public SingleAsyncPerformer(Callable<ListenableFuture<V>> operation,
                                BooleanSupplier condition,
                                Consumer<V> consumer) {
        mOperation = operation;
        mCondition = condition != null ? condition : () -> true;
        mConsumer = consumer != null ? consumer : r -> {};
    }

    public ListenableFuture<V> perform() {
        // Possibly return without the need for synchronization
        ListenableFuture<V> current = mCurrentOperation.get();
        if (current != null) return current;

        synchronized (this) {
            // Check again in case another thread came first after the check above
            current = mCurrentOperation.get();
            if (current != null) return current;

            if (!mCondition.getAsBoolean()) {
                return Futures.immediateFuture(null);
            }
            ListenableFuture<V> operation = MoreFutures.resultOf(mOperation);
            ListenableFuture<V> consumed = MoreFutures.consume(operation, mConsumer);
            return MoreFutures.setWhileOngoing(consumed, mCurrentOperation);
        }
    }

    public static class Builder<V> {

        private final Callable<ListenableFuture<V>> mOperation;
        private BooleanSupplier mCondition;
        private Consumer<V> mConsumer;


        public Builder(Callable<ListenableFuture<V>> operation) {
            mOperation = operation;
        }

        public Builder<V> onlyWhen(BooleanSupplier condition) {
            mCondition = condition;
            return this;
        }

        public Builder<V> supplyingTo(Consumer<V> consumer) {
            mConsumer = consumer;
            return this;
        }

        public SingleAsyncPerformer<V> build() {
            return new SingleAsyncPerformer<>(mOperation, mCondition, mConsumer);
        }
    }
}
