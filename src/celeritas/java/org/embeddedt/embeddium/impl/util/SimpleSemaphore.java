package org.embeddedt.embeddium.impl.util;

/**
 * Treasure Client: a permit counter standing in for java.util.concurrent.Semaphore,
 * which TeaVM doesn't provide. Chunk building runs on the browser's main thread,
 * so no locking is needed.
 */
public final class SimpleSemaphore {
    private int permits;

    public SimpleSemaphore(int permits) {
        this.permits = permits;
    }

    public void release() {
        this.permits++;
    }

    public void release(int count) {
        this.permits += count;
    }

    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    public boolean tryAcquire(int count) {
        if (this.permits >= count) {
            this.permits -= count;
            return true;
        }
        return false;
    }

    public void acquire() {
        if (!tryAcquire(1)) {
            throw new IllegalStateException("Would block forever: no permits on a single thread");
        }
    }

    public int availablePermits() {
        return this.permits;
    }
}
