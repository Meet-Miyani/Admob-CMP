package dev.avinya.admob.cmp.ads

// Desktop (JVM) has no AdMob SDK — ads are a no-op for now.
actual fun getAdController(): AdController = NoOpAdController()
