# `dev.avinya.ads` Package Rename Design

**Date:** 2026-07-25

## Goal

Move the public Kotlin package and platform namespaces of the ads library from
`avinya.tech.yt.ads` to the studio-owned `dev.avinya.ads` namespace.

Library branding is a separate decision. This migration does not rename the
project, Gradle modules, Maven artifacts, repository, or demo application.

## Package mapping

Every library package keeps its existing suffix:

```text
avinya.tech.yt.ads                      -> dev.avinya.ads
avinya.tech.yt.ads.appopen              -> dev.avinya.ads.appopen
avinya.tech.yt.ads.debug                -> dev.avinya.ads.debug
avinya.tech.yt.ads.debug.console        -> dev.avinya.ads.debug.console
avinya.tech.yt.ads.debug.tabs           -> dev.avinya.ads.debug.tabs
avinya.tech.yt.ads.debug.ui             -> dev.avinya.ads.debug.ui
avinya.tech.yt.ads.internal             -> dev.avinya.ads.internal
avinya.tech.yt.ads.nativead             -> dev.avinya.ads.nativead
avinya.tech.yt.ads.nativead.layout      -> dev.avinya.ads.nativead.layout
avinya.tech.yt.ads.nativead.rendering   -> dev.avinya.ads.nativead.rendering
avinya.tech.yt.ads.ui                   -> dev.avinya.ads.ui
```

Source and test directories move to paths matching their new package
declarations.

## Platform namespace mapping

```text
Android core namespace       avinya.tech.yt.ads.core      -> dev.avinya.ads.core
Android Compose namespace    avinya.tech.yt.ads.compose   -> dev.avinya.ads.compose
Android bundle namespace     avinya.tech.yt.ads.umbrella  -> dev.avinya.ads.bundle
iOS framework bundle ID      avinya.tech.yt.ads           -> dev.avinya.ads
```

The demo application remains:

```text
application ID and namespace  dev.avinya.admob.cmp
shared Android namespace      dev.avinya.admob.cmp.shared
demo source packages          dev.avinya.admob.cmp.*
```

## Publishing identity

This change does not alter:

```text
Maven group       dev.avinya.ads
Artifacts         admob-cmp, admob-cmp-core, admob-cmp-compose
Gradle modules    :admob-cmp, :admob-cmp-core, :admob-cmp-compose
Project name      AdmobCMP
```

Those names remain independently reviewable when the library branding decision
is made.

## Compatibility

Changing public Kotlin packages is a source- and binary-incompatible API change.
Consumers must replace imports from `avinya.tech.yt.ads.*` with
`dev.avinya.ads.*`.

The core and Compose ABI dumps must be regenerated after the move. No deprecated
compatibility aliases will be retained because aliases would preserve the old
public namespace and undermine the purpose of a complete pre-release rename.

## Documentation scope

Update current consumer-facing documentation, sample code, demo imports, build
configuration, and API dumps.

Historical design and implementation-plan documents remain unchanged because
they record the repository state and decisions that existed when they were
written.

## Verification

The migration is complete when:

1. Production sources, tests, demo imports, Gradle configuration, and API dumps
   contain no `avinya.tech.yt.ads` references.
2. Library source paths match `dev/avinya/ads`.
3. Core, Compose, and bundle Android and iOS compilation succeeds.
4. Core and Compose Android host tests and iOS simulator tests pass.
5. Kotlin ABI checks pass with regenerated dumps.
6. `git diff --check` reports no whitespace errors.
