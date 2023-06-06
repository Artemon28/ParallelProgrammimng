package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.lang.Integer.max
import java.util.concurrent.Phaser

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

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val nextCore = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E{
        val curNextCore = nextCore.value
        var curCore = core.value
        if (index >= curCore.cap){
            curCore = curNextCore
        }
        var res1: E? = null
        try {
            res1 = curNextCore.get(index)
        } catch(i: Exception){

        }
        return try {
            val res2 = curCore.get(index)
            if (res1 != null && res1 != res2){
                return res1
            }
             return res2
        } catch(i: IllegalStateException){
            curNextCore.get(index)
        }
    }

    override fun put(index: Int, element: E) {
        while (true){
            val curNextCore = nextCore.value
            var curCore = core.value
            if (index > curCore.cap){
                curCore = curNextCore
            }
            try {
                curCore.get(index)
                if (curCore.put(index, element)){
                    return
                } else {
                    if (curNextCore.actualSize.value <= index){
                        continue
                    }
                    if (curNextCore.put(index, element)) {
                        return
                    }
                }
            } catch(i: IllegalStateException){
                curNextCore.get(index)
                if (curNextCore.put(index, element)) {
                    return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while (true){
            val curNextCore = nextCore.value
            var curCore = core.value
            val copy = curCore
            if (curNextCore.cap >= curCore.cap * 2) {
                curCore = curNextCore
                for (i in 0 until copy.cap){
                    val temp: E? = copy.cancelUpdate(i)
                    if (temp == null){
                        continue
                    }
                    curCore.casToNull(i, temp)
                }
                if (!core.compareAndSet(copy, curCore)){
                    continue
                }
            }
            //TODO сейчас главная идея - все потоки сначала помогают сделать новый Core, не важно, есть место сейчас или
            // нет, когда они перенесли всё из Core в Next, два пути, или добавить себя, или начать по новой и создать
            // новый персональный и единственный Next.
            if (curCore.actualSize.value < curCore.cap){
                val result = curCore.pushBack(element)
                if (result == 2){
                    break
                } else if (result == 1) {
                    continue
                }
            } else{
                if (curNextCore.cap >= copy.cap * 2){
                    curCore = copy
                }
                val newCore: Core<E> = Core<E>(curCore.cap * 2)
                newCore.setSize(curCore.cap)
                if (nextCore.compareAndSet(curNextCore, newCore)){
                    for (i in 0 until curCore.cap){
                        val temp: E? = curCore.cancelUpdate(i)
                        if (temp == null){
                            continue
                        }
                        newCore.casToNull(i, temp)
                    }
                    if (core.compareAndSet(copy, newCore)){
                        if (newCore.pushBack(element) != 2){
                            continue
                        }
                        break
                    }
                }
            }
        }
    }

    override val size: Int get() {
        return max(core.value.actualSize.value, nextCore.value.actualSize.value)
    }
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<Value<E>>(capacity)
    private val _size = atomic(0)
    val actualSize = atomic(0)

    fun setSize(initSize: Int){
        _size.value = initSize
        actualSize.value = initSize
    }

    val size: Int
        get() {
            return _size.value
        }
    val cap: Int = capacity

    fun isFixed(index: Int): Boolean {
        if (array[index].value is FixedValue){
            return true
        }
        return false
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < actualSize.value)
        try {
            if (array[index].value != null && array[index].value !is MovedValue){
                return array[index].value!!.v as E
            }
            if (array[index].value is MovedValue){
                throw IllegalStateException()
            }
            throw IllegalArgumentException()
        } catch (e: ArrayIndexOutOfBoundsException){
            throw IllegalArgumentException()
        } catch (i: IllegalStateException){
            throw IllegalStateException()
        }
    }

    fun put(index: Int, element: E): Boolean {
        require(index < actualSize.value)
        try {
            while (true){
                val nowElement = array[index].value
                if (array[index].value is FixedValue<E>){
                    return false
                }
                if (array[index].compareAndSet(nowElement, CasualValue(element))){
                    return true
                }
            }
        } catch (e: ArrayIndexOutOfBoundsException){
            throw IllegalArgumentException()
        }
    }

    fun casToNull(index: Int, element: E):Boolean {
        require(index < actualSize.value)
        try {
            return array[index].compareAndSet(null, CasualValue(element))
        } catch (e: ArrayIndexOutOfBoundsException){
            throw IllegalArgumentException()
        }
    }

    fun pushBack(element: E?): Int { //возвращать не boolean, а указатель на причину выхода. Если там перенос
        // ячейки, то мы переносим. 0 - эвакуация 1 - в другом месте 2 - все хорошо
        val index = actualSize.value
//        val index = _size.getAndIncrement()
        if (index >= cap){
            return 0
        }
        while (true){
//            val nowElement = array[index].value //size is size - 1 + 1 element? so it's new one
            if (array[index].compareAndSet(null, CasualValue(element))){
                actualSize.compareAndSet(index, index + 1)
                return 2
            }
            actualSize.compareAndSet(index, index + 1)
            return 1
        }
    }

    fun cancelUpdate(index: Int): E?{
        while (true) {
            val temp = array[index].value
            val cell: Value<E> = if (temp == null || temp is MovedValue) {
                MovedValue(null)
            } else {
                FixedValue(temp.v)
            }
            if (array[index].compareAndSet(temp, cell)) {
                if (temp != null) {
                    return temp.v
                }
                return null
            }
        }
    }
}

abstract class Value<E>(v: E?){
    val v: E? = v
}

class CasualValue<E>(v: E?) : Value<E>(v) {
}

class MovedValue<E>(v: E?) : Value<E>(v) {
}

class FixedValue<E>(v: E?) : Value<E>(v) {
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

fun main(){
    test1()
}

fun test2(){
    val d = DynamicArrayImpl<Int>()
//    println(d.size)
    d.pushBack(1)
    d.pushBack(2)
//    println(d.size)
    d.pushBack(3)
    d.pushBack(4)
    d.pushBack(5)
    d.pushBack(6)
    println(d.get(5))

//    println(d.size)
}

fun test1(){
    val d = DynamicArrayImpl<Int>()
    val onFinish = Phaser(3 + 1)
    d.pushBack(4)
    Thread{
        d.pushBack(1)
//        d.pushBack(2)
//        d.pushBack(1)
//        println(d.get(5))
        onFinish.arrive()
    }.start()
    Thread{
        d.pushBack(3)
//        d.pushBack(1)
//        d.pushBack(4)
        onFinish.arrive()
    }.start()
    Thread.sleep(10)
    Thread{
        println(d.get(0))
//        d.pushBack(1)
//        d.pushBack(4)
        onFinish.arrive()
    }.start()

    onFinish.arriveAndAwaitAdvance()
}