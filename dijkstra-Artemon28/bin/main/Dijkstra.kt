package dijkstra
import kotlinx.atomicfu.atomic
import java.util.*
import java.util.concurrent.Phaser
import java.util.concurrent.ThreadLocalRandom
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

private val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> Integer.compare(o1!!.distance, o2!!.distance) }
private const val QUEUES_AMOUNT = 31

// Returns `Integer.MAX_VALUE` if a path has not been found.
val nodeAmount = atomic(1)
fun shortestPathParallel(start: Node) {
    val workers = Runtime.getRuntime().availableProcessors()
    // The distance to the start node is `0`
    start.distance = 0
    // Create a priority (by distance) queue and add the start node into it
    val q = MultiQueue(workers)
    q.insert(start)
    nodeAmount.value = 1
    // Run worker threads and wait until the total work is done
    val onFinish = Phaser(workers + 1) // `arrive()` should be invoked at the end by each worker
    repeat(workers) {
        thread {
            while (nodeAmount.value > 0) {
                // TODO Write the required algorithm here,
                // TODO break from this loop when there is no more node to process.
                // TODO Be careful, "empty queue" != "all nodes are processed".
                val cur: Node = q.delete() ?: continue
                for (e in cur.outgoingEdges) {
                    while (e.to.distance > cur.distance + e.weight) {
                        if (e.to.casDistance(e.to.distance, cur.distance + e.weight)) {
//                            e.to.casDistance(e.to.distance, cur.distance + e.weight)
                            nodeAmount.incrementAndGet()
                            q.insert(e.to)
                            nodeAmount.incrementAndGet()
                            q.insert(e.to)
                        }
                    }
                }
                nodeAmount.decrementAndGet()
            }
            onFinish.arrive()
        }
    }
    onFinish.arriveAndAwaitAdvance()
}

class PriorityQueueBlocked(workers: Int) {
    val q: PriorityQueue<Node> = PriorityQueue<Node>(workers, NODE_DISTANCE_COMPARATOR)
    val lock: java.util.concurrent.locks.ReentrantLock = java.util.concurrent.locks.ReentrantLock()
}

class MultiQueue(workers: Int) {
    private val mq: ArrayList<PriorityQueueBlocked> = arrayListOf()

    init {
        for (i in 0..QUEUES_AMOUNT) {
            mq.add(PriorityQueueBlocked(workers))
        }
    }

    fun insert(x: Node) {
        while (true) {
            val q = mq[ThreadLocalRandom.current().nextInt(0, QUEUES_AMOUNT + 1)]
            if (q.lock.tryLock()) {
                try {
                    q.q.add(x)
                } finally {
                    q.lock.unlock()
                }
                return
            }
        }
    }

    fun delete(): Node? {
        var counter = 0
        var lastNode: Node?
        while (true) {
            counter++
            var q: PriorityQueueBlocked? = null
            if (counter >= 3) {
                for (i in 0..QUEUES_AMOUNT) {
                    if (mq[i].q.size != 0) {
                        q = mq[i]
                        break
                    }
                }
                if (q == null) {
                    return null
                }
            }else {
                val j1 = ThreadLocalRandom.current().nextInt(0, QUEUES_AMOUNT + 1)
                val j2 = ThreadLocalRandom.current().nextInt(0, QUEUES_AMOUNT + 1)
                val node1 = mq[j1].q.peek()
                val node2 = mq[j2].q.peek()
                q = if (node1 == null && node2 == null) {
                    continue
                } else if (node1 == null || node2 == null) {
                    if (node1 == null) {
                        mq[j1]
                    } else {
                        mq[j2]
                    }
                } else if (node1.distance > node2.distance) {
                    mq[j1]
                } else {
                    mq[j2]
                }
            }

            if (q.lock.tryLock()) {
                try {
                    lastNode = q.q.poll()
                } finally {
                    q.lock.unlock()
                }
                if (lastNode == null){
                    continue
                }
                return lastNode
            }
        }
    }
}