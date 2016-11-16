package com.v1ct04.benchstack.webserver;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;

import java.util.Map;
import java.util.Random;

public class Arbitrator<FunctionType> {

    private final ListMultimap<Integer, FunctionType> mFunctions = LinkedListMultimap.create();
    private final Random mRandom = new Random();
    private int mWeightSum = 0;

    @SafeVarargs
    public static <Type> Arbitrator<Type> uniform(Type... funcs) {
        Arbitrator<Type> arbitrator = new Arbitrator<>();
        for (Type func : funcs) {
            arbitrator.addFunction(1, func);
        }
        return arbitrator;
    }

    public Arbitrator<FunctionType> addFunction(int weight, FunctionType func) {
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be a positive number");
        }
        mWeightSum += weight;
        mFunctions.put(weight, func);
        return this;
    }

    public FunctionType arbitrate() {
        if (mFunctions.isEmpty()) {
            throw new IllegalStateException("Must add function to arbitrator");
        }
        int sorted = mRandom.nextInt(mWeightSum);

        for (Map.Entry<Integer, FunctionType> candidate : mFunctions.entries()) {
            if (sorted < candidate.getKey()) {
                return candidate.getValue();
            }
            sorted -= candidate.getKey();
        }

        throw new RuntimeException("Arbitrator failed to pick candidate");
    }
}
