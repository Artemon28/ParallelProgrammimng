import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.math.abs

/**
 * Int-to-Int hash map with open addressing and linear probes.
 *
 * TODO: This class is **NOT** thread-safe.
 */
class IntIntHashMap {
    private val core = atomic(Core(INITIAL_CAPACITY))

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        val temp = core.value.getInternal(key)
        val temp2: Int
        if (temp == MOVED_NULL || temp < 0){
            val next = core.value.next.value
            if (next == null){
                if (temp == MOVED_NULL){
                    return 0
                } else
                    return temp * -1
            }
            temp2 = next.getInternal(key)
            if (temp2 != 0 && temp2 != temp * -1){
                if (temp2 == DEL_VALUE || temp2 == MOVED_NULL){
                    return 0
                }
                return abs(temp2)
            } else {
                if (temp == MOVED_NULL){
                    return 0
                } else
                    return temp * -1
            }
        }
        return toValue(temp)
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
    fun put(key: Int, value: Int): Int { //UPDATE
        require(key > 0) { "Key must be positive: $key" }
        require(isValue(value)) { "Invalid value: $value" }
        val temp = putAndRehashWhileNeeded(key, value)
        if (temp == DEL_VALUE){
            return 0
        }
        return temp
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
        val temp = putAndRehashWhileNeeded(key, DEL_VALUE)
        val temp2: Int
        if (temp == MOVED_NULL || temp < 0 || temp == DEL_VALUE){
            val next = core.value.next.value
            if (next == null){
                if (temp == MOVED_NULL || temp == DEL_VALUE){
                    return 0
                } else
                    return temp * -1
            }
            temp2 = next.putInternal(key, DEL_VALUE)
            if (temp2 != 0 && temp2 != temp * -1){
                return temp2
            } else {
                if (temp == MOVED_NULL || temp == DEL_VALUE){
                    return 0
                } else
                    return temp * -1
            }
        }
        return temp
    }

    private fun putAndRehashWhileNeeded(key: Int, value: Int): Int { //PUT
        while (true) {
            val curCore = core.value
            val oldValue = curCore.putInternal(key, value)
            if (oldValue != NEEDS_REHASH && oldValue != MOVED_NULL && oldValue >= 0) return oldValue
            core.compareAndSet(curCore, curCore.rehash())
        }
    }

    private class Core internal constructor(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val map = AtomicIntArray(2 * capacity)
        val shift: Int
        val next : AtomicRef<Core?> = atomic(null)

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (map[index].value != key) { // optimize for successful lookup
                if (map[index].value == NULL_KEY) return NULL_VALUE // not found -- no value
                if (++probes >= MAX_PROBES) return NULL_VALUE
                if (index == 0) index = map.size
                index -= 2
            }
            // found key -- return value
            return map[index + 1].value
        }

        fun putInternal(key: Int, value: Int): Int {
            while(true){
                var index = index(key)
                var probes = 0
                var curKey = map[index].value
                while (curKey != key) { // optimize for successful lookup
                    if (curKey == NULL_KEY) {
                        // not found -- claim this slot
                        if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                        if (map[index].compareAndSet(curKey, key)) {
                            break
                        }
                        if (map[index].value == key){
                            break
                        }
                    }
                    if (++probes >= MAX_PROBES) return NEEDS_REHASH
                    if (index == 0) index = map.size
                    index -= 2
                    curKey = map[index].value
                }
                // found key -- update value
                val oldValue = map[index + 1].value
                if (oldValue < 0){
                    return oldValue
                }
                if (oldValue == 0 && value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                if (map[index + 1].compareAndSet(oldValue, value))
                    return oldValue
                if (map[index + 1].value == MOVED_NULL)
                    return MOVED_NULL
                if (map[index + 1].value < 0){
                    return map[index + 1].value
                }
            }

        }

        fun putInternalNull(key: Int, value: Int): Int {
            var index = index(key)
            var curKey = map[index].value
            while (curKey != key) { // optimize for successful lookup
                if (curKey == NULL_KEY) {
                    // not found -- claim this slot
                    if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                    if (map[index].compareAndSet(curKey, key)){
                        break
                    }
                    if (map[index].value == key){
                        break
                    }
                }
                if (index == 0) index = map.size
                index -= 2
                curKey = map[index].value
            }
            // found key -- update value
            map[index + 1].compareAndSet(NULL_VALUE, value)
            return NULL_VALUE
        }

        fun rehash(): Core {
            while (true){
                val newCore: Core
                if (next.value != null){
                    newCore = next.value!!
                } else{
                    newCore = Core(map.size) // map.length is twice the current capacity
                    if (!next.compareAndSet(null, newCore)) continue
                }
                var index = 0
                while (index < map.size) {
                    val temp = cancelUpdate(index + 1)
                    if (isValue(temp)) {
                        newCore.putInternalNull(map[index].value, temp)
                    }
                    index += 2
                }
                return newCore
            }
        }

        fun cancelUpdate(index: Int): Int{
            while (true){
                val temp = map[index].value
                if (temp == MOVED_NULL){
                    return MOVED_NULL
                }
                if (temp < 0){
                    return temp * -1
                }
                val cell: Int = if (temp == NULL_VALUE || temp == DEL_VALUE) {
                    MOVED_NULL
                } else {
                    temp * -1
                }
                if (map[index].compareAndSet(temp, cell)) {
                    return temp
                }
            }
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        fun index(key: Int): Int = (key * MAGIC ushr shift) * 2
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
private const val MAX_PROBES = 8 // max number of probes to find an item
private const val NULL_KEY = 0 // missing key (initial value)
private const val NULL_VALUE = 0 // missing value (initial value)
private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed

private const val MOVED_NULL = Int.MIN_VALUE // moved

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0
