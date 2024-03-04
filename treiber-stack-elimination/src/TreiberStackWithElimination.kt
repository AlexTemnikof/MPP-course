import java.util.concurrent.*
import java.util.concurrent.atomic.*

/**
 * @author Темников Алексей
 */
open class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    private val eliminationArray = AtomicReferenceArray<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    protected open fun tryPushElimination(element: E): Boolean {
        val randIndex = randomCellIndex();
        if (eliminationArray.compareAndSet(randIndex, null, element)) {
            repeat(10) {
                if (eliminationArray.compareAndSet(randIndex, null, null))
                    return true;
            }
            if (eliminationArray.compareAndSet(randIndex, element, null)) {
                return false;
            }
            return true;
        }
        return false;
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val randIndex = randomCellIndex();
        val element = eliminationArray.get(randIndex) ?: return null;
        if (eliminationArray.compareAndSet(randIndex, element, null)) {
            @Suppress("UNCHECKED_CAST")
            return element as E;
        }
        return null;
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(eliminationArray.length())

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!

        // Initially, all cells are in EMPTY state.
        private val CELL_STATE_EMPTY = null

        // `tryPopElimination()` moves the cell state
        // to `RETRIEVED` if the cell contains element.
        private val CELL_STATE_RETRIEVED = Any()
    }
}
