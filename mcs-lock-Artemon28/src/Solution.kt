import java.util.concurrent.Phaser
import java.util.concurrent.atomic.*
import java.util.concurrent.locks.LockSupport

class Solution(val env: Environment) : Lock<Solution.Node> {
    // todo: необходимые поля (val, используем AtomicReference)
    val tail = AtomicReference<Node?>(null)

    override fun lock(): Node {
        val my = Node() // сделали узел
        my.next.value = null
        my.locked.value = true
        val pred = tail.getAndSet(my)
        if (pred != null){
            pred.next.value = my
            while (my.locked.value) env.park()
        }
        return my // вернули узел
    }

    override fun unlock(node: Node) {
        if (node.next.value == null){
            if (tail.compareAndSet(node, null)){
                return
            } else {
                while (node.next.value == null) env.park()
            }
        }
        node.next.value!!.locked.value = false
        env.unpark(node.next.value!!.thread)
    }

    class Node {
        val thread = Thread.currentThread() // запоминаем поток, которые создал узел
        val locked = AtomicReference<Boolean>(false)
        val next = AtomicReference<Node?>(null)
        // todo: необходимые поля (val, используем AtomicReference)
    }
}

fun main(){
    class eeee: Environment{
        override fun park() = LockSupport.park()
        override fun unpark(thread: Thread) = LockSupport.unpark(thread)
    }
    val newEnv = eeee()
    val g = Solution(newEnv)
    val list = arrayListOf<Int>(10)
    val onFinish = Phaser(3 + 1)
    Thread{
        val node = g.lock()
        list.add(10)
        g.unlock(node)
        onFinish.arrive()
    }.start()
    Thread{
        val node = g.lock()
        list[0] = 1
        g.unlock(node)
        onFinish.arrive()
    }.start()
    Thread{
        val node = g.lock()
        list[0] = 10
        g.unlock(node)
        onFinish.arrive()
    }.start()
    onFinish.arriveAndAwaitAdvance()
    println(list)
}