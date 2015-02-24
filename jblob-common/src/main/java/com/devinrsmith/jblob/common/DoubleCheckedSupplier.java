package com.devinrsmith.jblob.common;

import java.util.function.Supplier;

public class DoubleCheckedSupplier<T> implements Supplier<T> {
    public static <T> Supplier<T> of(Supplier<T> supplier) {
        return supplier instanceof DoubleCheckedSupplier ? supplier : new DoubleCheckedSupplier<>(supplier);
    }

    private final Supplier<T> supplier;
    private transient volatile T t;

    private DoubleCheckedSupplier(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    @Override
    public T get() {
        // use of local variable so we can control number of volatile read / writes
        T local = t; // vol read
        if (local == null) {
            synchronized (supplier) {
                local = t; // vol read
                if (local == null) {
                    local = supplier.get();
                    if (local == null) {
                        throw new IllegalStateException("Supplier should not return a null result");
                    }
                    t = local; // vol write
                }
            }
        }
        return local;
    }
}