package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node<E>(prev = first, element = null, next = null)
    init {
        first.setNext(last)
    }

    private val head = atomic(first)

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        while (true){
            var firstNode = head.value
            var secondNode = head.value.next!!
            while (secondNode.element.value != null && secondNode.element.value!!.v!! < element){
                firstNode = secondNode
                secondNode = secondNode.next!!
            }
            val curElement = secondNode.element.value
            if (curElement != null && (curElement.v == element || curElement is Removed)){
                return false
            }
            val newNode = Node<E>(prev = null, element = Alive(element), next = secondNode)
            if (firstNode.casNext(secondNode, newNode)){
                return true
            }
        }

//        return true
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {
        while (true){
            var firstNode = head.value
            var secondNode = head.value.next!!
            while (secondNode.element.value != null && secondNode.element.value!!.v!! < element){
                firstNode = secondNode
                secondNode = secondNode.next!!
            }
            val curElement = secondNode.element.value
            if (curElement == null || curElement.v != element || curElement is Removed){
                return false
            }
            if (secondNode.casElement(curElement, Removed(curElement.v))){
                return false
            }
            if (firstNode.casNext(secondNode, secondNode.next)){
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var secondNode = head.value.next!!
        while (secondNode.element.value != null && secondNode.element.value!!.v!! < element){
            secondNode = secondNode.next!!
        }
        val curElement = secondNode.element.value
        if (curElement == null  || curElement.v != element || curElement is Removed){
            return false
        }
        return true
    }
}

abstract class Value<E : Comparable<E>>(val v: E?){}

class Alive<E : Comparable<E>>(v: E?) : Value<E>(v) {}

class Removed<E : Comparable<E>>(v: E?) : Value<E>(v) {}


private class Node<E : Comparable<E>> (prev: Node<E>?, element: Value<E>?, next: Node<E>?) {
    private val _element = atomic(element) // `null` for the first and the last nodes
    val element get() = _element
    fun casElement(expected: Value<E>?, update: Value<E>?) =
        _element.compareAndSet(expected, update)

    private val _prev = atomic(prev)
    val prev get() = _prev.value
    fun setPrev(value: Node<E>?) {
        _prev.value = value
    }
    fun casPrev(expected: Node<E>?, update: Node<E>?) =
        _prev.compareAndSet(expected, update)

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}

fun main(){
    val ls = LinkedListSet<Int>()
    ls.add(2)
}