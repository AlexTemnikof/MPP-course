@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

/**
 * @author Темников Алексей
 */

import java.util.concurrent.atomic.*

class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(element = null, prev = null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.get();
            val node = Node(element, curTail);
            if (curTail.extractedOrRemoved) {
                curTail.removePhysically();
            }
            if (curTail.next.compareAndSet(null, node)) {
                if (curTail.extractedOrRemoved) {
                    curTail.removePhysically();
                }
                tail.compareAndSet(curTail, node);
                return
            } else {
                val next = curTail.next.get();
                if (next != null) {
                    tail.compareAndSet(curTail, next);
                }
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.get();
            val curHeadNext = curHead.next.get();
            if (curHeadNext == null) {
                return null;
            }
            if (head.compareAndSet(curHead, curHeadNext)) {
                curHeadNext.prev.set(null);
                if (curHeadNext.markExtractedOrRemoved()) {
                    return curHeadNext.element;
                }
            }
        }
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = head.get()
        while (true) {
            val next = node.next.get()
            if (next == null) return false
            node = next
            if (node.element == element && node.remove()) return true
        }
    }

    /**
     * This is an internal function for tests.
     * DO NOT CHANGE THIS CODE.
     */
    override fun validate() {
        check(head.get().prev.get() == null) {
            "`head.prev` must be null"
        }
        check(tail.get().next.get() == null) {
            "tail.next must be null"
        }
        // Traverse the linked list
        var node = head.get()
        while (true) {
            if (node !== head.get() && node !== tail.get()) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of the queue"
                }
            }
            val nodeNext = node.next.get()
            // Is this the end of the linked list?
            if (nodeNext == null) break
            // Is next.prev points to the current node?
            val nodeNextPrev = nodeNext.prev.get()
            check(nodeNextPrev != null) {
                "The `prev` pointer of node with element ${nodeNext.element} is `null`, while the node is in the middle of the queue"
            }
            check(nodeNextPrev == node) {
                "node.next.prev != node; `node` contains ${node.element}, `node.next` contains ${nodeNext.element}"
            }
            // Process the next node.
            node = nodeNext
        }
    }

    private class Node<E>(
        var element: E?,
        prev: Node<E>?
    ) {
        val next = AtomicReference<Node<E>?>(null)
        val prev = AtomicReference(prev)

        private val _extractedOrRemoved = AtomicBoolean(false)
        val extractedOrRemoved
            get() =
                _extractedOrRemoved.get()

        fun markExtractedOrRemoved(): Boolean =
            _extractedOrRemoved.compareAndSet(false, true)

        fun remove(): Boolean {
            val removed = markExtractedOrRemoved();
            if (removed) {
                removePhysically();
            }
            return removed;
        }

        fun removePhysically() {
            val curNext = next.get();
            val curPrev = prev.get()
            if (curNext == null || curPrev == null) {
                return
            }
            curPrev.next.getAndSet(curNext);
            while (true) {
                val prevCurNext = curNext.prev.get();
                if (prevCurNext == null) {
                    break;
                }
                if (curNext.prev.compareAndSet(prevCurNext, curPrev)) {
                    break;
                }
            }
            if (curPrev.extractedOrRemoved) {
                curPrev.removePhysically();
            }
            if (curNext.extractedOrRemoved) {
                curNext.removePhysically();
            }
        }
    }
}