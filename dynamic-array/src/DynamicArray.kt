/**
 * @author Темников Алексей
 */

package mpp.dynamicarray

import kotlinx.atomicfu.*

interface DynamicArray<E> {
    /**
     * Returns the element located in the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the [size] of this array.
     */
    fun get(index: Int): E

    /**
     * Puts the specified [element] into the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the [size] of this array.
     */
    fun put(index: Int, element: E)

    /**
     * Adds the specified [element] to this array
     * increasing its [size].
     */
    fun pushBack(element: E)

    /**
     * Returns the current size of this array,
     * it increases with [pushBack] invocations.
     */
    val size: Int
}

@Suppress("UNCHECKED_CAST")
class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val _size = atomic(0);


    override fun get(index: Int): E {
        if (index >= _size.value) {
            throw IllegalArgumentException();
        }
        var core = this.core.value;
        while (true) {
            val value = core.getArray()[index].value;
            if (value is Wrapper) {
                if (!value.finished) {
                    return value.element as E;
                }
                core = core.getNext().value!!;
            } else {
                return value as E;
            }
        }
    }

    override fun put(index: Int, element: E) {
        if (index >= _size.value) {
            throw IllegalArgumentException();
        }
        var core = this.core.value;
        while (true) {
            val value = core.getArray()[index].value;
            if (value is Wrapper) {
                if (!value.finished) {
                    core.getNext().value!!.getArray()[index].compareAndSet(null, value.element);
                    core.getArray()[index].compareAndSet(value, Wrapper(value.element, true));
                    if (core.getNext().value!!.getArray()[index].compareAndSet(value.element, element)) {
                        return;
                    }
                } else {
                    core = core.getNext().value!!;
                    continue;
                }
            } else {
                if (core.getArray()[index].compareAndSet(value, element)) {
                    return;
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val core = core.value;
            val size = _size.value;
            if (size >= core.capacity) {
                val next = resize(core) ?: continue;
                this.core.compareAndSet(core, next);
                continue;
            }
            if (core.getArray()[size].compareAndSet(null, element)) {
                _size.compareAndSet(size, size + 1);
                return;
            } else {
                _size.compareAndSet(size, size + 1);
            }
        }
    }

    private fun resize(core: Core<E>) : Core<E>? {
        if (core.getNext().value == null) {
            core.getNext().compareAndSet(null, Core(2 * core.capacity));
        }

        val next = core.getNext().value ?: return null;

        for (i in 0 until core.capacity) {
            while (true) {
                val value = core.getArray()[i].value;
                if (value is Wrapper) {
                    if (!value.finished) {
                        next.getArray()[i].compareAndSet(null, value.element);
                        core.getArray()[i].compareAndSet(value, Wrapper(value.element, true));
                        break;
                    } else {
                        break;
                    }
                } else {
                    if (core.getArray()[i].compareAndSet(value, Wrapper(value, false))) {
                        continue;
                    }
                }
            }
        }

        return next;
    }

    override val size: Int get() = _size.value

    private class Wrapper(val element: Any?, val finished: Boolean);

    private class Core<E>(
        val capacity: Int,
    ) {
        private val array = atomicArrayOfNulls<Any>(capacity)
        private val next: AtomicRef<Core<E>?> = atomic(null);

        fun getArray(): AtomicArray<Any?> {
            return this.array;
        }

        fun getNext(): AtomicRef<Core<E>?> {
            return this.next;
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME