package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    //за счёт того, что мы пишем AtomicIntArray, мы выделяем отдельные кэшлайны для регситров
    /*
    1) запускаясь последовательно get() всегда выведет число больше или равное предыдущему, так как он считает или тот же
    массив или массив с увеличенным значением
    2)запускаясб параллельно два get() могут выдать одинаковое число или один будет меньше другого, тогда разницу
    между ними можно будет описать как inc() функции, которые пришли между двумя запросами get()
    Таким образом, за счёт того, что мы всегда увеличиваем счётчик только на 1, мы всегда можем расставить в каком
    порядке выполнялись вызовы функций
     */

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val randomInteger = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
        counters[randomInteger].incrementAndGet()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var sum = 0
        for (i in 0 until ARRAY_SIZE){
            sum += counters[i].value
        }
        return sum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME