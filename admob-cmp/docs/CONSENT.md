# Consent & Privacy (UMP)

Consent is integrated into initialization (see [SETUP.md](SETUP.md) for
`ConsentMode`). The `adManager.consent` controller exposes the UMP state.

## Status

```kotlin
val consentStatus by adManager.consent.status.collectAsState()

when (consentStatus) {
    ConsentStatus.Unknown -> Unit       // not determined yet
    ConsentStatus.Required -> Unit      // form must be shown
    ConsentStatus.Obtained -> Unit      // user answered
    ConsentStatus.NotRequired -> Unit   // regulation does not apply
    is ConsentStatus.Failed -> Unit     // update failed (error attached)
}

val canRequestAds by adManager.consent.canRequestAds.collectAsState()
```

`canRequestAds` is the gate that matters: the SDK refuses ad requests with
`AdErrorCode.CONSENT_REQUIRED` until it is true (unless `SkipConsent`).

## Privacy options form

Show a "Privacy Settings" entry point when — and only when — UMP says one is
required (this is the GDPR re-consent affordance):

```kotlin
val privacyRequirement by adManager.consent.privacyOptionsRequirementStatus.collectAsState()

if (privacyRequirement == PrivacyOptionsRequirementStatus.Required) {
    Button(onClick = { scope.launch { adManager.consent.showPrivacyOptions() } }) {
        Text("Privacy Settings")
    }
}
```

> Do not gate this on `ConsentStatus.Obtained` — users who declined consent
> still must be able to reopen the form.

## Debug geography & test devices

```kotlin
adManager.gatherConsentAndInitialize(
    AdConfig(
        androidAppId = "...",
        iosAppId = "...",
        testMode = true,
        debugGeography = ConsentDebugGeography.Eea,   // force EEA consent flow
        testDeviceIds = listOf("YOUR-DEVICE-HASH")
    )
)
```

`ConsentDebugGeography`: `Disabled`, `Eea`, `NotEea`. Debug settings apply only
when `testMode = true`.

## Reset (debug builds)

```kotlin
scope.launch { adManager.consent.resetConsentForDebug() }
```

## Re-checking without UI

```kotlin
scope.launch { adManager.consent.requestConsentInfoUpdate(config) }
```

Updates `status` / `canRequestAds` / `privacyOptionsRequirementStatus` without
presenting a form.
