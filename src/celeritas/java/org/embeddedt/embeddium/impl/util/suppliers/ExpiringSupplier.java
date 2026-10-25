package org.embeddedt.embeddium.impl.util.suppliers;


import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ExpiringSupplier<T> implements Supplier<T> {
    private final Supplier<T> supplier;
    private final long expiryTime;
    private long lastTime;
    private T lastValue;

    public ExpiringSupplier(Supplier<T> supplier, long duration, TimeUnit unit) {
        this.supplier = supplier;
        this.expiryTime = TimeUnit.NANOSECONDS.convert(duration, unit);
    }

    @Override
    public T get() {
        if (lastValue == null || (System.nanoTime() - lastTime) >= expiryTime) {
            lastValue = supplier.get();
            Objects.requireNonNull(lastValue, "Returned value may not be null");
            lastTime = System.nanoTime();
        }
        return lastValue;
    }
}
