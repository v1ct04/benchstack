package com.v1ct04.benchstack.concurrent;

import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BottomlessQueue<Type> {

    private final Queue<Type> mItems;
    private final SingleAsyncPerformer<List<Type>> mFetcher;

    public BottomlessQueue(Callable<ListenableFuture<List<Type>>> fetchOperation) {
        mItems = new ConcurrentLinkedQueue<>();
        mFetcher =
                SingleAsyncPerformer.doing(fetchOperation)
                        .onlyWhen(mItems::isEmpty)
                        .supplyingTo(mItems::addAll)
                        .build();
    }

    public ListenableFuture<Type> poll() {
        Type item = mItems.poll();
        if (item == null) {
            return Futures.transform(mFetcher.perform(), (Object r) -> poll());
        }
        return Futures.immediateFuture(item);
    }

    public <OType> ListenableFuture<OType> pollTransform(AsyncFunction<Type, OType> f) {
        return Futures.transform(poll(), f);
    }

    public ListenableFuture<Type> peek() {
        Type item = mItems.peek();
        if (item == null) {
            return Futures.transform(mFetcher.perform(), (Object r) -> peek());
        }
        return Futures.immediateFuture(item);
    }

    public <OType> ListenableFuture<OType> peekTransform(AsyncFunction<Type, OType> f) {
        return Futures.transform(peek(), f);
    }

    public BottomlessQueue<Type> clear() {
        mItems.clear();
        return this;
    }
}
