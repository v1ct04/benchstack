package com.v1ct04.benchstack.concurrent;

import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BottomlessQueue<Type> {

    private final BlockingQueue<Type> mItems = new LinkedBlockingQueue<>();
    private final AsyncFunction<Integer, List<Type>> mItemSupplier;
    private final int mRefillCount;

    private ListenableFuture<?> mOngoingRequest;

    public BottomlessQueue(AsyncFunction<Integer, List<Type>> itemSupplier) {
        this(itemSupplier, 10);
    }

    public BottomlessQueue(AsyncFunction<Integer, List<Type>> itemSupplier, int refillCount) {
        mItemSupplier = itemSupplier;
        mRefillCount = refillCount;
    }

    public ListenableFuture<Type> poll() {
        Type item = mItems.poll();
        if (item == null) {
            return Futures.transform(refill(), (Object r) -> poll());
        }
        return Futures.immediateFuture(item);
    }

    public <OType> ListenableFuture<OType> pollTransform(AsyncFunction<Type, OType> f) {
        return Futures.transform(poll(), f);
    }

    public ListenableFuture<Type> peek() {
        Type item = mItems.peek();
        if (item == null) {
            return Futures.transform(refill(), (Object r) -> peek());
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

    private synchronized ListenableFuture<?> refill() {
        if (mOngoingRequest != null) {
            return mOngoingRequest;
        } else if (!mItems.isEmpty()) {
            return Futures.immediateFuture(null);
        }
        try {
            mOngoingRequest = Futures.transform(mItemSupplier.apply(mRefillCount), this::finishRefill);
            return mOngoingRequest;
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private synchronized Object finishRefill(List<Type> items) {
        mOngoingRequest = null;
        mItems.addAll(items);
        return null; // need this in order to be used in Futures#transform
    }
}
