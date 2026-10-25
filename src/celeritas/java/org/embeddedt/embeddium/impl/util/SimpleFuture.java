package org.embeddedt.embeddium.impl.util;

/**
 * Treasure Client: stand-in for CompletableFuture. Everything runs on the
 * main thread, so a future is always already complete.
 */
public final class SimpleFuture<T> {
	private final T value;

	private SimpleFuture(T value) {
		this.value = value;
	}

	public static <T> SimpleFuture<T> completedFuture(T value) {
		return new SimpleFuture<>(value);
	}

	public T join() {
		return value;
	}

	public boolean isDone() {
		return true;
	}
}
