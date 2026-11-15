package com.yeokma.saju.data

import android.content.Context

/**
 * 태어난 곳 목록(레포의 data/places.tsv, 서버와 같은 파일).
 * 말한 문장에서 앞에서부터 가장 긴 지명을 찾고, 상위 지역이 같이 나오면 그 아래로 좁힌다.
 * 특별시·광역시는 그 이름까지, 도는 시·군까지만 받는다. 결과가 하나면 확정, 여럿이면 어느 곳인지 물어본다.
 */
class Places private constructor(private val places: List<Place>, private val keys: Map<String, List<Int>>) {
    data class Place(val full: String, val label: String, val parent: Int?)

    private val longest = keys.keys.maxOf { it.length }

    /** 도처럼 아래에 시·군이 있는 곳의 시·군 목록 */
    fun children(p: Place): List<Place> { val id = places.indexOf(p); return places.filter { it.parent == id } }

    private fun isAncestor(a: Int, x0: Int): Boolean {
        var x = places[x0].parent
        while (x != null) { if (x == a) return true; x = places[x].parent }
        return false
    }

    /** (찾은 말, 후보들) */
    fun find(text: String): Pair<String, List<Place>> {
        val s = text.filter { !it.isWhitespace() }
        val hit = mutableListOf<Int>()
        var word = ""
        var i = 0
        while (i < s.length) {
            var found = 0
            for (len in minOf(longest, s.length - i) downTo 1) {
                val ids = keys[s.substring(i, i + len)] ?: continue
                ids.forEach { if (it !in hit) hit += it }
                word = s.substring(i, i + len)
                found = len
                break
            }
            i += maxOf(found, 1)
        }
        val under = hit.filter { x -> hit.any { a -> isAncestor(a, x) } }
        val pool = under.ifEmpty { hit }
        val out = pool.filter { a -> pool.none { x -> isAncestor(a, x) } }.sorted()
        return word to out.map { places[it] }
    }

    companion object {
        @Volatile private var cached: Places? = null

        fun get(context: Context): Places = cached ?: synchronized(this) {
            cached ?: load(context.assets.open("places.tsv").bufferedReader(Charsets.UTF_8).readText()).also { cached = it }
        }

        private fun load(tsv: String): Places {
            val places = mutableListOf<Place>()
            val byFull = HashMap<String, Int>()
            val shortTop = HashMap<String, String>()
            val keys = HashMap<String, MutableList<Int>>()
            tsv.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                val c = line.split('\t')
                val parent = c[0]; val name = c[1]
                val aliases = c.getOrElse(3) { "" }.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                val full = if (parent.isEmpty()) name else "$parent $name"
                if (parent.isEmpty()) shortTop[name] = aliases.lastOrNull() ?: name
                // 고를 때 보여 줄 이름: 맨 위 지역은 줄여서 ("경기도 광주시" → "경기 광주시")
                val label = full.split(' ').let { parts -> (listOf(shortTop[parts[0]] ?: parts[0]) + parts.drop(1)).joinToString(" ") }
                val id = places.size
                places += Place(full, if (parent.isEmpty()) name else label, byFull[parent])
                byFull[full] = id
                val ks = mutableListOf(name)
                if (parent.isNotEmpty() && name.length >= 3 && name.last() in "시군구") ks += name.dropLast(1)
                ks += aliases
                ks.forEach { k -> keys.getOrPut(k) { mutableListOf() }.let { if (id !in it) it += id } }
            }
            return Places(places, keys)
        }
    }
}
