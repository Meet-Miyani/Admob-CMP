package dev.avinya.admob.showcase.data.seed

import dev.avinya.admob.showcase.data.db.entity.ArticleEntity

/**
 * Deterministic local content.
 *
 * The showcase has no network layer on purpose: ad behaviour is hard enough
 * to reason about without content loading also being a variable. Same input,
 * same 126 articles, every run, offline.
 */
object ArticleSeed {

    private val sections = listOf("Kotlin", "Compose", "Multiplatform", "Android", "iOS", "Tooling")

    private val topics = listOf(
        "Structured concurrency in practice",
        "What recomposition actually costs",
        "Reading a klib ABI dump",
        "Expect and actual, revisited",
        "Stable types and skippability",
        "Coroutine cancellation you can trust",
        "Paging without the pain",
        "A tour of the memory model",
        "Designing for two platforms at once",
        "When to reach for a state machine",
        "Build times are a feature",
        "Snapshot state, explained",
        "Interop that does not leak",
        "Testing without an emulator",
        "Dependency injection, by hand",
        "Immutability and its discontents",
        "Lifecycles across platforms",
        "Draw, layout, measure",
        "Persistence that survives a refactor",
        "Naming things, still hard",
        "The cost of an abstraction",
    )

    private val authors = listOf("R. Elder", "M. Okonkwo", "S. Lindqvist", "A. Bhatt", "J. Moreau", "T. Nakamura")

    private fun body(topic: String, section: String, index: Int): String = buildString {
        append("$topic is one of those areas where the obvious approach and the correct ")
        append("approach diverge quietly, and the divergence only shows up under load.\n\n")
        append("Most $section code starts simple. A single call site, a single owner, ")
        append("no contention. The trouble begins when the second caller arrives and ")
        append("nobody has decided who owns the state.\n\n")
        append("The rule that has held up best: make the boundary explicit before you ")
        append("make it fast. An explicit boundary can be optimised later. An implicit ")
        append("one has to be discovered first, usually during an incident.\n\n")
        append("There is a version of this argument that goes too far, and it ends in ")
        append("six layers of indirection for a two-line function. Judgement number ")
        append("$index: does the abstraction pay for the reading cost it imposes?\n\n")
        append("If it does not, delete it. That is the whole technique.")
    }

    /** 126 articles: 21 topics across 6 sections. Every 7th is premium. */
    fun articles(): List<ArticleEntity> = buildList {
        var index = 0
        sections.forEach { section ->
            topics.forEach { topic ->
                val premium = index % 7 == 6
                add(
                    ArticleEntity(
                        id = "article-${index.toString().padStart(3, '0')}",
                        title = topic,
                        author = authors[index % authors.size],
                        body = body(topic, section, index),
                        section = section,
                        // Fixed base epoch minus a per-index offset: deterministic and
                        // strictly descending, so feed order is stable across runs.
                        publishedAt = BASE_PUBLISHED_AT - index * ONE_HOUR_MILLIS,
                        readTimeMin = 4 + index % 9,
                        isPremium = premium,
                        unlockCostCoins = if (premium) 50 else 0,
                    )
                )
                index++
            }
        }
    }

    private const val BASE_PUBLISHED_AT = 1_767_225_600_000L // 2026-01-01T00:00:00Z
    private const val ONE_HOUR_MILLIS = 3_600_000L
}
