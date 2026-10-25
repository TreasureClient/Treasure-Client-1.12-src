package org.embeddedt.embeddium.impl.util.collections;


public interface WriteQueue<E> {
    void ensureCapacity(int numElements);

    void enqueue(E e);
}
