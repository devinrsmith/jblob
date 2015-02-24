package com.devinrsmith.jblob.common;

import java.util.Iterator;
import java.util.function.Supplier;

/**
 * Created by dsmith on 2/20/15.
 */
public class LazyIterator<T> implements Iterator<T> {
    private final Supplier<Iterator<T>> supplier;

    public LazyIterator(Supplier<Iterator<T>> supplier) {
        this.supplier = DoubleCheckedSupplier.of(supplier);
    }

    @Override
    public boolean hasNext() {
        return supplier.get().hasNext();
    }

    @Override
    public T next() {
        return supplier.get().next();
    }
}
