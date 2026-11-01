package dev.taskbound.core;

import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;

final class WeightedPicker {
    private WeightedPicker() {
    }

    static <T> T pick(List<T> values, ToDoubleFunction<T> weight, RandomGenerator random) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Cannot pick from an empty list");
        }
        double total = 0.0;
        for (T value : values) {
            double itemWeight = weight.applyAsDouble(value);
            if (!Double.isFinite(itemWeight) || itemWeight <= 0.0) {
                throw new IllegalArgumentException("Weights must be finite and positive");
            }
            total += itemWeight;
        }
        if (!Double.isFinite(total)) {
            throw new IllegalArgumentException("Total weight is too large");
        }
        double cursor = random.nextDouble(total);
        for (T value : values) {
            cursor -= weight.applyAsDouble(value);
            if (cursor < 0.0) {
                return value;
            }
        }
        return values.get(values.size() - 1);
    }
}
