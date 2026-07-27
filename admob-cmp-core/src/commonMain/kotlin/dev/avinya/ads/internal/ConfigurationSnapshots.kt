package dev.avinya.ads.internal

import dev.avinya.ads.AdConfig
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.AdRequestOptions
import dev.avinya.ads.GlobalRequestConfiguration

internal fun GlobalRequestConfiguration.ownedSnapshot(): GlobalRequestConfiguration =
    copy(testDeviceIds = testDeviceIds.toList())

internal fun AdConfig.ownedSnapshot(): AdConfig = copy(
    globalRequestConfiguration = globalRequestConfiguration.ownedSnapshot(),
    debugOptions = debugOptions.copy(
        consentTestDeviceIds = debugOptions.consentTestDeviceIds.toList()
    ),
    initializationHooks = initializationHooks.toList()
)

internal fun AdRequestOptions.ownedSnapshot(): AdRequestOptions = copy(
    keywords = keywords.toSet(),
    neighboringContentUrls = neighboringContentUrls.toSet(),
    categoryExclusions = categoryExclusions.toSet(),
    customTargeting = customTargeting
        .mapValues { (_, values) -> values.toList() }
        .toMap(),
    googleExtras = googleExtras.toMap()
)

internal fun AdPlacement.ownedSnapshot(): AdPlacement =
    copy(requestOptions = requestOptions.ownedSnapshot())
