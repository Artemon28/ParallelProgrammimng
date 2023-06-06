import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(AtomicReference(null), "none")
        head = atomic(dummy)
        tail = atomic(dummy)
    }
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true){
            val t = tail.value
            val h = head.value
            if (t == h || t.isSender()){
                val newNode = Node(AtomicReference(element), "sender")
                val res = suspendCoroutine<Any> { cont ->
                    newNode.contin = cont
                    if (t.next.compareAndSet(null, newNode)){
                        tail.compareAndSet(t, newNode)
                    } else{
                        cont.resume("retry")
                    }
                }
                if (res == "retry"){
                    tail.compareAndSet(t, t.next.value!!)
                    continue
                }
                return
            } else {
                if (t != tail.value){
                    continue
                }
                val newHead = h.next.value ?: continue
                if (newHead.isSender()){
                    continue
                }
                if (newHead.x.compareAndSet(null, element)){
                    newHead.contin!!.resume(Unit)
                    head.compareAndSet(h, newHead)
                    return
                } else {
                    head.compareAndSet(h, h.next.value!!)
                    continue
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E{
        while (true){
            val t = tail.value
            val h = head.value
            if (t == h || t.isReceiver()){
                val newNode = Node(AtomicReference<E?>(null), "receiver")
                val res = suspendCoroutine<Any> { cont ->
                    newNode.contin = cont
                    if (t.next.compareAndSet(null, newNode)){
                        tail.compareAndSet(t, newNode)
                    } else{
                        cont.resume("retry")
                    }
                }
                if (res == "retry"){
                    tail.compareAndSet(t, t.next.value!!)
                    continue
                }
                return newNode.x.get()!!
            } else {
                if (t != tail.value){
                    continue
                }
                val newHead = h.next.value ?: continue
                if (newHead.isReceiver()){
                    continue
                }
                val answer = newHead.x.get()
                if (newHead.x.compareAndSet(answer, null) && answer != null){
                    newHead.contin!!.resume(Unit)
                    head.compareAndSet(h, newHead)
                    return answer
                } else {
                    head.compareAndSet(h, h.next.value!!)
                    continue
                }
            }
        }
    }

    private class Node<E>(var x: AtomicReference<E?>, val nodeRole: String) {
        val next = atomic<Node<E>?>(null)
        var contin: Continuation<Unit>? = null

        fun isSender(): Boolean{
            if (nodeRole == "sender"){
                return true
            }
            return false
        }

        fun isReceiver(): Boolean{
            if (nodeRole == "receiver"){
                return true
            }
            return false
        }
    }
}