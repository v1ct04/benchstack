package com.v1ct04.benchstack.concurrent;

public class Interruptibles {

    public static void uninterruptibly(InterruptibleRunnable r) {
        untilSuccess(() -> {r.run(); return true;});
    }

    public static void untilSuccess(InterruptibleOperation op) {
        boolean interrupted = false;
        while (true) {
            try {
                boolean success = op.execute();
                if (success) break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public interface InterruptibleRunnable {
        void run() throws InterruptedException;
    }

    public interface InterruptibleOperation {
        boolean execute() throws InterruptedException;
    }
}
