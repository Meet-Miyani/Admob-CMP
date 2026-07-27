package dev.avinya.ads

@RequiresOptIn(
    message = "This API connects AdMob CMP implementation artifacts and is not a stable consumer API.",
    level = RequiresOptIn.Level.ERROR
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY
)
public annotation class InternalAdMobCmpApi
