import java.util.concurrent.atomic.*

/**
 * @author Темников Алексей
 */
class FAABasedQueue<E> : Queue<E> {
    private val head: AtomicReference<Segment>;
    private val tail: AtomicReference<Segment>;
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    init {
        val dummy = Segment(0);
        head = AtomicReference(dummy);
        tail = AtomicReference(dummy);
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.get();
            val i = enqIdx.getAndIncrement();
            val s = findSegment(curTail, i / SEGMENT_SIZE);
            moveTailForward(s);
            if (s.cells.compareAndSet((i % SEGMENT_SIZE).toInt(), null, element)) {
                return;
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // Is this queue empty?
        while (true) {
            if (deqIdx.get() >= enqIdx.get()) return null;
            val curHead = head.get();
            val i = deqIdx.getAndIncrement();
            val s = findSegment(curHead, i / SEGMENT_SIZE);
            moveHeadForward(s);
            if (!s.cells.compareAndSet((i % SEGMENT_SIZE).toInt(), null, POISONED)) {
                return s.cells.get((i % SEGMENT_SIZE).toInt()) as E;
            }
        }
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var s = start;
        while (s.id < id) {
            if (s.next.get() == null) {
                s.next.compareAndSet(null, Segment(s.id + 1));
            } else {
                s = s.next.get()!!;
            }
        }
        return s;
    }

    private fun moveTailForward(s: Segment) {
        while (true) {
            val curTail = tail.get();
            if (curTail.id > s.id) {
                return;
            }
            if (tail.compareAndSet(curTail, s)) {
                return;
            }
        }
    }

    private fun moveHeadForward(s: Segment) {
        while(true) {
            val curHead = head.get();
            if (curHead.id > s.id) {
                return;
            }
            if (head.compareAndSet(curHead, s)) {
                return;
            }
        }
    }
}

private class Segment(val id: Long) {
    val next = AtomicReference<Segment?>(null)
    val cells = AtomicReferenceArray<Any?>(SEGMENT_SIZE)
}

// DO NOT CHANGE THIS CONSTANT
private const val SEGMENT_SIZE = 2

private val POISONED = Any()
