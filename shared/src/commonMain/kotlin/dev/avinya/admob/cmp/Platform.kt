package dev.avinya.admob.cmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform