import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E{
        val refGet = Ref<E>(index)
        return refGet.value
    }

    fun cas(index: Int, expected: E, update: E): Boolean{
        while (true){
            val cell = a[index]
            val refGet = Ref<E>(index)
            val temp = refGet.value
            if (cell.compareAndSet(expected, update)){
                return true
            } else if (temp == expected) {
                continue
            } else {
                return false
            }
        }
    }


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
            while (true){
                val temp = refA.value
                if (a[refA.index].compareAndSet(expected1, desc)){
                    desc.complete()
                    return desc.refOutcome.value == "SUCCESS"
                } else if (temp == expected1) {
                    continue
                } else {
                    return false
                }
            }
        } else if (index1 > index2) {
            refA = Ref<E>(index2)
            refB = Ref<E>(index1)
            desc = RDCSSDescriptor(refA, expected2, update2,
                refB, expected1, update1)
            while (true){
                val temp = refA.value
                if (a[refA.index].compareAndSet(expected2, desc)){
                    desc.complete()
                    return desc.refOutcome.value == "SUCCESS"
                } else if (temp == expected2) {
                    continue
                } else {
                    return false
                }
            }
        } else {
            if (expected1 != expected2){
                return false
            }
            return cas(index1, expected1, update2)
        }
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
        fun cas(exp: T, upd: T){
            v.compareAndSet(exp, upd)
        }
    }

    inner class RDCSSDescriptor(
        val ra: Ref<E>, val expectA: E, val updateA: E,
        val rb: Ref<E>, val expectB: E, val updateB: E
    ): Descriptor() {
        val refOutcome = Ref2("UNDECIDED")
        var desc2: DCSSDescriptor<String>? = DCSSDescriptor<String>(rb, expectB, this,
            refOutcome, "UNDECIDED")
        override fun complete() {
            val second = a[rb.index].value
            if (desc2!!.outcome.value != "FAIL"){
                if (second is AtomicArray<*>.DCSSDescriptor<*>){
                    rb.value
                } else if (second !is Descriptor){
                    while (true){
                        val temp = rb.value
                        if (a[rb.index].compareAndSet(expectB, desc2)){
                            break
                        } else if (temp == expectB) {
                            continue
                        } else {
                            desc2!!.outcome.compareAndSet("UNDECIDED","FAIL")
                            break
                        }
                    }
                    rb.value //DCSS for B ends here
                } else if (second != this){
                    rb.value
                    while (true){
                        val temp = rb.value
                        if (a[rb.index].compareAndSet(expectB, desc2)){
                            break
                        } else if (temp == expectB) {
                            continue
                        } else {
                            desc2!!.outcome.compareAndSet("UNDECIDED","FAIL")
                            break
                        }
                    }
                    rb.value
                }

                if (desc2!!.outcome.value == "UNDECIDED"){
                    while (true){
                        val temp = rb.value
                        if (a[rb.index].compareAndSet(expectB, desc2)){
                            break
                        } else if (temp == expectB) {
                            continue
                        } else {
                            desc2!!.outcome.compareAndSet("UNDECIDED","FAIL")
                            break
                        }
                    }
                    rb.value
                }
            }

            if (desc2!!.outcome.value == "SUCCESS"){
                refOutcome.cas("UNDECIDED", "SUCCESS")
                val ind1 = ra.index
                if (a[ind1].value != this){
                    ra.value
                }
                a[ind1].compareAndSet(this, updateA)
                val ind2 = rb.index
                if (a[ind2].value != this){
                    rb.value
                }
                a[ind2].compareAndSet(this, updateB)
            } else  {
                refOutcome.cas("UNDECIDED", "FAIL")
                a[ra.index].compareAndSet(this, expectA)
                a[rb.index].compareAndSet(this, expectB)
            }
        }
    }

    inner class DCSSDescriptor<G>(
        val ra: Ref<E>, val expectA: E, val updateA: Any,
        var rb: Ref2<G>, val expectB: G): Descriptor() {
        val outcome = atomic<String>("UNDECIDED")
        override fun complete() {
            if (rb.value == expectB){
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
    val g = AtomicArray<Int>(10, 0)
    println(g.cas2(2, 0, 1, 0, 0, 1))
    println(g.cas2(4, 0, 1, 2, 1, 2))
    println(g.get(0))
    println(g.get(2))
    println(g.get(4))
}