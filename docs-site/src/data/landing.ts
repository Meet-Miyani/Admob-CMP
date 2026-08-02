export interface LandingFormat {
  slug: string;
  name: string;
  href: string;
  blurb: string;
  api: string;
  /**
   * The format's on-screen size, as a developer would state it. Shown beside
   * the API signature on the landing page.
   *
   * The matching *geometry* — where the ad region sits inside the phone
   * viewport that the placement plate draws — lives in landing.css keyed by
   * slug, because it is presentation. Only the wording lives here.
   */
  dimension: string;
}

export const formats: readonly LandingFormat[] = [
  {
    slug: 'banner',
    name: 'Banner',
    href: '/formats/banner/',
    blurb: 'Inline rectangular ad anchored inside Compose layout.',
    api: 'BannerAdView(placement)',
    dimension: '320 × 50 dp',
  },
  {
    slug: 'interstitial',
    name: 'Interstitial',
    href: '/formats/interstitial/',
    blurb: 'Full-screen ad shown at a natural transition point.',
    api: 'adManager.interstitial(placement)',
    dimension: 'full screen',
  },
  {
    slug: 'rewarded',
    name: 'Rewarded',
    href: '/formats/rewarded/',
    blurb: 'Full-screen ad that grants a reward on completion.',
    api: 'adManager.rewarded(placement)',
    dimension: 'full screen',
  },
  {
    slug: 'rewarded-interstitial',
    name: 'Rewarded interstitial',
    href: '/formats/rewarded/#how-is-a-rewarded-interstitial-different',
    blurb: 'Full-screen rewarded ad shown at a natural transition point.',
    api: 'adManager.rewardedInterstitial(placement)',
    dimension: 'full screen',
  },
  {
    slug: 'app-open',
    name: 'App-open',
    href: '/formats/app-open/',
    blurb: 'Full-screen ad shown when the app returns to the foreground.',
    api: 'AppOpenAdCoordinator(manager, controller, config)',
    dimension: 'full screen',
  },
  {
    slug: 'native',
    name: 'Native',
    href: '/formats/native/',
    blurb: 'Compose-rendered ad built from a typed layout DSL and pooled across screens.',
    api: 'NativeAdView(placement, itemKey, layout)',
    dimension: 'sized by your layout',
  },
];

export interface CapabilityRow {
  capability: string;
  admobCmp: string;
  basicAds: string;
}

export const capabilityVerifiedOn = '31 July 2026';
export const basicAdsRepo = 'https://github.com/LexiLabs-App/basic-ads';

export const capabilities: readonly CapabilityRow[] = [
  {
    capability: 'Banner ads',
    admobCmp: 'Yes',
    basicAds: 'Yes',
  },
  {
    capability: 'Interstitial ads',
    admobCmp: 'Yes',
    basicAds: 'Yes',
  },
  {
    capability: 'Rewarded ads',
    admobCmp: 'Yes',
    basicAds: 'Yes',
  },
  {
    capability: 'Rewarded interstitial ads',
    admobCmp: 'Yes',
    basicAds: 'Yes',
  },
  {
    capability: 'App-open ads',
    admobCmp: 'Yes',
    basicAds: 'Not offered',
  },
  {
    capability: 'Native ads',
    admobCmp: 'Yes',
    basicAds: 'Not offered',
  },
  {
    capability: 'Native ad layout DSL and pooling',
    admobCmp: 'adLayout {} plus NativeAdPool max-size accounting',
    basicAds: 'Not applicable',
  },
  {
    capability: 'UMP consent inside initialization',
    admobCmp: 'gatherConsentAndInitialize, three consent strategies, privacy-options form',
    basicAds: 'Consent request',
  },
  {
    capability: 'iOS ATT ordering',
    admobCmp: 'tracking authorization between consent and initialize',
    basicAds: 'Not documented',
  },
  {
    capability: 'Paid and revenue events',
    admobCmp: 'AdEvent.Paid with AdValue and ResponseInfo',
    basicAds: 'Not documented',
  },
  {
    capability: 'Mediation adapter hooks',
    admobCmp: 'AdInitializationHook at three initialization points',
    basicAds: 'Not documented',
  },
  {
    capability: 'Kotlin/Native test linking',
    admobCmp: 'Published dev.avinya.ads.admob-cmp Gradle plugin',
    basicAds: 'Not addressed',
  },
  {
    capability: 'Generated API reference',
    admobCmp: 'Yes',
    basicAds: 'Yes',
  },
  {
    capability: 'Maven Central publication',
    admobCmp: 'Yes',
    basicAds: 'Yes',
  },
];

export interface RoadmapItem {
  title: string;
  status: string;
}

export const roadmapItems: readonly RoadmapItem[] = [
  {
    title: 'Swift Package Manager dependency import',
    status:
      'Gated on four unmet upstream conditions (swiftPMDependencies); Maven-consumer propagation remains an open unknown and the project refuses to depend on an Alpha build-tool feature.',
  },
  {
    title: 'Native video events on Android',
    status:
      'Blocked on the upstream SDK. iOS exposes GADVideoControllerDelegate with five video events; no equivalent Android callback surface is available.',
  },
];

export const repoUrl = 'https://github.com/Meet-Miyani/admob-compose-multiplatform';
export const trademarkStatement =
  'Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.';

export const authorName = 'Meet Miyani';
export const studioName = 'Avinya';
export const studioUrl = 'https://avinya.dev';
/** The repo owner's profile, derived so it cannot drift from `repoUrl`. */
export const authorUrl = repoUrl.split('/').slice(0, 4).join('/');

/**
 * Why the library exists. Kept here rather than in the component so the wording
 * is reviewable in one place alongside the other public copy.
 *
 * TODO(origin-app): the app this was extracted from is not live yet, so the
 * third paragraph describes it generically. When it ships, name and link it.
 */
export const originStory = {
  paragraphs: [
    'Putting ads into a Compose Multiplatform app meant leaving Compose. The Google Mobile Ads SDKs are Android and iOS libraries with callback-based APIs, so the shared module ended up holding an expect/actual seam, two sets of platform glue, and a consent flow bolted on afterwards.',
    'Samples and libraries already covered this ground, and they helped. What was still wanted was ads shaped like the rest of a Compose Multiplatform codebase: composables for the ad surfaces, suspend functions and StateFlow for the lifecycle, and consent gathered as part of initialization rather than as a step to remember.',
    'So it was built inside an app that was being written, then extracted once the shape had settled. The APIs here are the ones that had to hold up on real screens: a native ad pool with max-size accounting, retry and cache behaviour with the numbers written down, and an iOS ordering — consent, then tracking authorization, then initialize — that the library sequences for you.',
  ],
} as const;

export interface LandingMeta {
  mavenCoordinate: string;
  gradlePlugin: string;
  kotlinVersion: string;
  composeMultiplatformVersion: string;
  androidMinSdk: number;
  iosDeploymentTarget: string;
  licenseName: string;
}

export const landingMeta: LandingMeta = {
  mavenCoordinate: 'dev.avinya.ads:admob-cmp:1.1.0',
  gradlePlugin: 'dev.avinya.ads.admob-cmp:1.1.0',
  kotlinVersion: '2.3.20',
  composeMultiplatformVersion: '1.11.1',
  androidMinSdk: 26,
  iosDeploymentTarget: '15.0',
  licenseName: 'Apache License 2.0',
};
