package com.github.quiltservertools.ledger.callbacks;

import java.lang.reflect.Array;
import java.util.function.Function;

/**
 * Loader-neutral replacement for Fabric API's event class. It keeps the same
 * {@code register} and {@code invoker} shape so callers don't need to change.
 */
public final class Event<T> {
    private final Class<? super T> type;
    private final Function<T[], T> invokerFactory;
    private volatile T[] listeners;
    private volatile T invoker;

    Event(Class<? super T> type, Function<T[], T> invokerFactory) {
        this.type = type;
        this.invokerFactory = invokerFactory;
        this.listeners = newArray(0);
        this.invoker = invokerFactory.apply(this.listeners);
    }

    public T invoker() {
        return invoker;
    }

    public synchronized void register(T listener) {
        T[] updated = newArray(listeners.length + 1);
        System.arraycopy(listeners, 0, updated, 0, listeners.length);
        updated[listeners.length] = listener;
        listeners = updated;
        invoker = invokerFactory.apply(updated);
    }

    @SuppressWarnings("unchecked")
    private T[] newArray(int length) {
        return (T[]) Array.newInstance(type, length);
    }
}
