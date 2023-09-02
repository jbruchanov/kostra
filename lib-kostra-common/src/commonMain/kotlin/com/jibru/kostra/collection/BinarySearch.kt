package com.jibru.kostra.collection

fun LongArray.binarySearch(value: Long, fromIndex: Int = 0, toIndex: Int = this.size): Int {
    //JVM implementation
    var low: Int = fromIndex
    var high: Int = toIndex - 1

    while (low <= high) {
        val mid = low + high ushr 1
        val midVal: Long = this[mid]
        if (midVal < value) low = mid + 1 else if (midVal > value) high = mid - 1 else return mid // key found
    }
    return -(low + 1)
}