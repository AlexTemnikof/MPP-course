/**
 * @author Темников Алексей
 */

import Result
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        if (!combinerLock.compareAndSet(false, true)) {
            val index = randomCellIndex();
            tasksForCombiner.set(index, element);
            while(true) {
                if (tasksForCombiner.get(index) is Result<*>) {
                    tasksForCombiner.set(index, null);
                    return;
                }
                if (combinerLock.compareAndSet(false, true)) {
                    if (tasksForCombiner.get(index) is Result<*>) {
                        tasksForCombiner.set(index, null);
                        combinerLock.set(false);
                        return;
                    }
                    tasksForCombiner.set(index, null);
                    break;
                }
            }
        }
        queue.addLast(element);
        traverseAnArray();
        combinerLock.set(false);
    }

    override fun dequeue(): E {
        if (!combinerLock.compareAndSet(false, true)) {
            val index = randomCellIndex();
            tasksForCombiner.set(index, Dequeue);
            while(true) {
                val cellValue = tasksForCombiner.get(index);
                if (cellValue is Result<*>) {
                    val value = cellValue.value;
                    tasksForCombiner.set(index, null);
                    return value as E;
                }
                if (combinerLock.compareAndSet(false, true)) {
                    val lastCellValue = tasksForCombiner.get(index);
                    if (lastCellValue is Result<*>) {
                        val value = (lastCellValue).value;
                        tasksForCombiner.set(index, null);
                        combinerLock.set(false);
                        return value as E;
                    }
                    tasksForCombiner.set(index, null);
                    break;
                }
            }
        }
        val value = queue.removeFirstOrNull();
        traverseAnArray();
        combinerLock.set(false);
        return value as E;
    }

    private fun traverseAnArray() {
        val resultClass = Result::class;
        for (i in 0 until tasksForCombiner.length()) {
            val value = tasksForCombiner.get(i);
            if (value == Dequeue) {
                val result = queue.removeFirstOrNull();
                tasksForCombiner.set(i, Result(result));
            } else if (value != null && !resultClass.isInstance(value)){
                queue.addLast(value as E);
                tasksForCombiner.set(i, Result(null));
            }
        }
    }

    private fun randomCellIndex(): Int =
        Thread.currentThread().id.toInt() % TASKS_FOR_COMBINER_SIZE
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!
private object Dequeue
private class Result<V>(
    val value: V
)