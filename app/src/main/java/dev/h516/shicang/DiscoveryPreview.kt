package dev.h516.shicang

import kotlin.random.Random

/** A session seed, not composition timing, determines the entire discovery order. */
internal fun discoveryOrder(ids: List<String>, seed: Long, initial: String = ""): List<String> {
    val pool = ids.distinct().sorted().shuffled(Random(seed))
    return if (initial in pool) listOf(initial) + pool.filterNot { it == initial } else pool
}

/** Every accessible item in this archive is shown once before the next media round. */
internal fun discoveryPreview(a: Archive, round: Int, seed: Long, random: Boolean): Photo? {
    if (a.deleted != 0L) return null
    val pool = a.photos.distinctBy { it.id }
    if (!random) return pool.firstOrNull { it.id == a.cover } ?: pool.firstOrNull()
    if (pool.isEmpty()) return null
    val deck = pool.sortedBy { it.id }.shuffled(Random(seed xor a.id.hashCode().toLong()))
    return deck[Math.floorMod(round, deck.size)]
}

internal data class DiscoveryActions(val random: Boolean, val toggle: () -> Unit, val refresh: () -> Unit)
