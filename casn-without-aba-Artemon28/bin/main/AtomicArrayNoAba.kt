import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E{
        val refGet = Ref<E>(index)
        return refGet.value
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        if (a[index1].value == null || a[index2].value == null){
            throw IllegalArgumentException()
        }
        val refA: Ref<E>
        val refB: Ref<E>
        val desc: RDCSSDescriptor
        if (index1 < index2){
            refA = Ref<E>(index1)
            refB = Ref<E>(index2)
            desc = RDCSSDescriptor(refA, expected1, update1,
                refB, expected2, update2)
            if (a[refA.index].value is Descriptor){
                refA.value
            }
            if (a[refA.index].compareAndSet(expected1, desc)){
                desc.complete()
                return desc.outcome.value == "SUCCESS"
            }
        } else if (index1 > index2) {
            refA = Ref<E>(index2)
            refB = Ref<E>(index1)
            desc = RDCSSDescriptor(refA, expected2, update2,
                refB, expected1, update1)
            if (a[refA.index].value is Descriptor){
                refA.value
            }
            if (a[refA.index].compareAndSet(expected2, desc)){
                desc.complete()
                return desc.outcome.value == "SUCCESS"
            }
        } else {
            return cas(index1, expected1, (expected1 as Int + 2) as E)
        }
        return false
    }

    abstract class Descriptor(){
        abstract fun complete()
    }

    inner class Ref<T>(initial: Int){
        val index = initial

        var value: T
            get() {
                while (true){
                    val ind = index
                    val cur = a[ind].value
                    when(cur) {
                        is Descriptor -> cur.complete()
                        else -> return cur as T
                    }
                }
            }
            set(value) {}
    }

    inner class Ref2<T>(initial: T){
        val v = atomic<Any?>(initial)

        var value: T
            get() {
                while (true){
                    val cur = v.value
                    when(cur) {
                        is Descriptor -> cur.complete()
                        else -> return cur as T
                    }
                }
            }
            set(upd: T) {
                while (true){
                    val cur = v.value
                    when(cur) {
                        is Descriptor -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd))
                            return
                    }
                }
            }
    }

    inner class RDCSSDescriptor(
        val ra: Ref<E>, val expectA: E, val updateA: E,
        val rb: Ref<E>, val expectB: E, val updateB: E
    ): Descriptor() {
        val outcome = atomic<String>("UNDECIDED")
        var desc2: DCSSDescriptor<String>? = DCSSDescriptor<String>(rb, expectB, this,
            Ref2(this.outcome.value), "UNDECIDED")
        override fun complete() {

            if (a[rb.index].value is AtomicArrayNoAba<*>.DCSSDescriptor<*>){
                rb.value
            } else if (a[rb.index].value !is Descriptor){
                a[rb.index].compareAndSet(expectB, desc2)//DCSS for B starts here
                rb.value //DCSS for B ends here
            } else if (a[rb.index].value != this){
                rb.value
                a[rb.index].compareAndSet(expectB, desc2)
                rb.value
            }

            if (desc2!!.outcome.value == "UNDECIDED"){
                a[rb.index].compareAndSet(expectB, desc2)
                rb.value
            }

            if (desc2!!.outcome.value == "SUCCESS"){
                this.outcome.compareAndSet("UNDECIDED", "SUCCESS")
                val ind1 = ra.index
                a[ind1].compareAndSet(this, updateA)
                val ind2 = rb.index
                a[ind2].compareAndSet(this, updateB)
            } else {
                this.outcome.compareAndSet("UNDECIDED", "FAIL")
                a[ra.index].compareAndSet(this, expectA)
                a[rb.index].compareAndSet(this, expectB)
            }
        }
    }

    inner class DCSSDescriptor<G>(
        val ra: Ref<E>, val expectA: E, val updateA: Any,
        val rb: Ref2<G>, val expectB: G): Descriptor() {
        val outcome = atomic<String>("UNDECIDED")
        override fun complete() {
            if (rb.value === expectB){
                outcome.compareAndSet("UNDECIDED", "SUCCESS")
                a[ra.index].compareAndSet(this, updateA)
            } else {
                outcome.compareAndSet("UNDECIDED", "FAIL")
                a[ra.index].compareAndSet(this, expectA)
            }
        }
    }
}

fun main(){
    val g = AtomicArrayNoAba<Int>(10, 0)
    println(g.cas2(2, 0, 1, 0, 0, 1))
    println(g.cas2(4, 0, 1, 2, 1, 2))
    println(g.get(0))
    println(g.get(2))
    println(g.get(4))
}