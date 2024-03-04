/**
 * @author Темников Алексей
 */

import kotlinx.atomicfu.AtomicIntArray
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Int-to-Int hash map with open addressing and linear probes.
 */
class IntIntHashMap {
    private var core = AtomicReference(Core(INITIAL_CAPACITY))

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(core.get().getInternal(key))
    }

    /**
     * Changes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key   a positive key.
     * @param value a positive value.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key or value are not positive, or value is equal to
     * [Integer.MAX_VALUE] which is reserved.
     */
    fun put(key: Int, value: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        require(isValue(value)) { "Invalid value: $value" }
        return toValue(putAndRehashWhileNeeded(key, value))
    }

    /**
     * Removes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key a positive key.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    fun remove(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(putAndRehashWhileNeeded(key, DEL_VALUE))
    }

    private fun putAndRehashWhileNeeded(key: Int, value: Int): Int {
        while (true) {
            val curCore = core.get();
            val oldValue = core.get().putInternal(key, value)
            if (oldValue != NEEDS_REHASH) return oldValue
            core.compareAndSet(curCore, curCore.rehash())
        }
    }
    private class Core(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val map = AtomicIntArray(2 * capacity)
        val shift: Int
        private val next = AtomicReference<Core?>(null);

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key);
            var probes = 0;
            while (map[index].value != key) { // optimize for successful lookup
                val curKey = map[index].value;
                if (curKey == key) {
                    break;
                }
                if (curKey == NULL_KEY) {
                    return NULL_VALUE;
                }
                if (++probes >= MAX_PROBES) {
                    return NEEDS_REHASH;
                }
                if (index == 0) index = map.size;
                index -= 2;
            }
            // found key -- return value
            val value = map[index + 1].value;
            if (value == DEL_VALUE) {
                return NULL_VALUE;
            }
            if (value == PROCEEDED) {
                return this.next.get()!!.getInternal(key);
            }
            return abs(value);
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key);
            var probes = 0;
            while (map[index].value != key) { // optimize for successful lookup
                val curKey = map[index].value;
                if (curKey == key) {
                    break;
                }
                if (curKey == NULL_KEY) {
                    // not found -- claim this slot
                    if (value == DEL_VALUE) {
                        return NULL_VALUE;
                    } // remove of missing item, no need to claim slot
                    if (!map[index].compareAndSet(NULL_KEY, key)) {
                        continue;
                    } else {
                        break;
                    }
                }
                if (++probes >= MAX_PROBES) {
                    return NEEDS_REHASH;
                }
                if (index == 0) index = map.size;
                index -= 2;
            }
            // found key -- update value
            while (true) {
                val oldValue = map[index + 1].value;
                if (oldValue == PROCEEDED) {
                    return next.get()!!.putInternal(key, value);
                }
                if (oldValue < 0) {
                    return NEEDS_REHASH;
                }
                if (map[index + 1].compareAndSet(oldValue, value)) {
                    if (oldValue == DEL_VALUE) {
                        return NULL_VALUE;
                    }
                    return abs(oldValue);
                }
            }
        }

        fun rehash(): Core {
            this.next.compareAndSet(null, Core(map.size));
            val newCore = this.next.get()!!;
            var index = 0;
            while (index < map.size) {
                val value = map[index + 1].value;
                if (value == DEL_VALUE || value == NULL_VALUE) {
                    map[index + 1].compareAndSet(value,PROCEEDED);
                }
                if (value == PROCEEDED){
                    index += 2;
                    continue;
                }
                if (isValue(map[index + 1].value)) {
                    map[index + 1].compareAndSet(value,-value);
                }
                if (value < 0) {
                    val result = newCore.copyPair(map[index].value, -value);
                    assert(result != -1000) { "Unexpected result during rehash: $result" }
                    map[index + 1].compareAndSet(value,PROCEEDED);
                    index += 2;
                }
            }
            return newCore;
        }

        fun copyPair(key: Int, value: Int) : Int {
            var index = index(key);
            var probes = 0;
            while (true) {
                val curKey = map[index].value;
                if (curKey == NULL_KEY) {
                    if (map[index].compareAndSet(NULL_KEY, key)) {
                        map[index + 1].compareAndSet(NULL_VALUE, value)
                        return value;
                    }
                    continue;
                }
                if (curKey == key) {
                    map[index + 1].compareAndSet(NULL_VALUE, value);
                    return value;
                }
                if (++probes >= MAX_PROBES) {
                    return -1000;
                }
                if (index == 0) index = map.size;
                index -= 2;
            }
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        fun index(key: Int): Int = (key * MAGIC ushr shift) * 2
    }
}
private const val PROCEEDED = Int.MIN_VALUE;
private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
private const val MAX_PROBES = 8 // max number of probes to find an item
private const val NULL_KEY = 0 // missing key (initial value)
private const val NULL_VALUE = 0 // missing value (initial value)
private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0