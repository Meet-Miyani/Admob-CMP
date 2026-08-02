<!--
Release notes template for dev.avinya.ads:admob-cmp.

Copy everything below the marker into the GitHub release body and fill the
<ANGLE BRACKET> fields. Delete any section that does not apply — an empty
heading is worse than a missing one.

Release name format (the tag is bare `X.Y.Z`; the *name* carries the `v`):

    vX.Y.Z — <what a user gains, in five words or fewer>

Keep "Compose Multiplatform" or "Kotlin Multiplatform" in the name. A release
page is often the first search result for this project, and a name like
"v1.2.0 — bug fixes" matches nothing anyone types.

Rules that are not negotiable:

1. The opener states what the library IS. Assume the reader arrived from a
   search engine and has never heard of it.
2. Gradle coordinates appear above the fold.
3. Docs links appear above the fold.
4. Never write `compare/<old>...<new>` unless BOTH tags exist. Verify with
   `git tag --list` before publishing; only `1.1.0` existed as of 2026-07-31,
   which is why the changelog page is the default target.
5. The trademark line is the last line, every time.
6. If the public ABI changed, say so explicitly and link the migration notes.
   The ABI is frozen (admob-cmp/CLAUDE.md invariant 12), so in practice this
   line reads "the public ABI is unchanged".
-->

--- COPY BELOW THIS LINE ---

## <One-line headline: the change, phrased as what the user gains>

**AdMob CMP** is a Compose Multiplatform AdMob SDK for Android and iOS — banner,
interstitial, rewarded, rewarded interstitial, app-open and native ads behind one
Kotlin API, with UMP consent in the initialization flow, mediation, and
paid/revenue events.

```kotlin
// commonMain
implementation("dev.avinya.ads:admob-cmp:<VERSION>")
```

📖 [Quickstart](https://ads.avinya.dev/start/quickstart/) ·
[Installation](https://ads.avinya.dev/start/installation/) ·
[iOS setup](https://ads.avinya.dev/start/ios-setup/) ·
[All docs](https://ads.avinya.dev)

---

### What's new

<Lead with the problem in the reader's words, then the fix. If there is a
diagnostic string users paste into a search engine — a linker error, a stack
trace, an exception message — quote it verbatim in a fenced block. That string
is the query this page should rank for.>

### <Feature or fix heading>

<Worked example in Kotlin. Show the smallest complete snippet that works, not a
fragment.>

Full guide: <https://ads.avinya.dev/... link the specific page>

### Upgrading from <PREVIOUS VERSION>

```kotlin
implementation("dev.avinya.ads:admob-cmp:<VERSION>")
```

<State whether any source change is required. If the public ABI is unchanged,
say so in bold — it is the single most useful sentence in the release for
someone deciding whether to upgrade today.>

### Compatibility

| admob-cmp | Kotlin | Compose Multiplatform | Android minSdk | iOS deployment target |
|---|---|---|---|---|
| <VERSION> | <KOTLIN> | <CMP> | <MINSDK> | <IOS> |

Full matrix: https://ads.avinya.dev/reference/compatibility/

### Notes

- The Gradle plugin (`dev.avinya.ads:admob-cmp-gradle-plugin`) is versioned in
  lockstep with the library. Always use matching versions.
- <Anything platform-specific that did or did not change.>

**Full changelog:** https://ads.avinya.dev/reference/changelog/

---

*Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are
trademarks of Google LLC.*
