package com.v1ct04.benchstack.concurrent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Synchronization abstraction for notifying multiple clients of certain events,
 * without the need for managing the actual list of listening clients as the
 * notification will be handled asynchronously when calling the
 * {@link Receiver#signaled()} method, similarly to how it's done for threads
 * interruption.
 *
 * Both Signaler and Receiver are thread-safe, and thus can be used by multiple
 * threads with no problem (though the most common case is probably when a single
 * thread uses a single Receiver whose Signaler can be signaled from any thread.
 *
 * Alike {@link Thread#interrupted()} as well, the {@link Receiver#signaled()}
 * method will clear the "signaled" "flag" when it's called, no matter how many
 * signals have been sent since the last check.
 */
public class Signaler {

    private final AtomicInteger mSignalCount = new AtomicInteger(0);

    public void signal() {
        mSignalCount.incrementAndGet();
    }

    public Receiver receiver() {
        return new Receiver(mSignalCount);
    }

    public static class Receiver {
        private final AtomicInteger mWatched;
        private final AtomicInteger mLastSeenCount;

        private Receiver(AtomicInteger watched) {
            mWatched = watched;
            mLastSeenCount = new AtomicInteger(mWatched.get());
        }

        public boolean signaled() {
            int curr = mWatched.get();
            if (mLastSeenCount.get() == curr) return false;
            return mLastSeenCount.getAndSet(curr) != curr;
        }
    }
}
