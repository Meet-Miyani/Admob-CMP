export interface LandingFormat {
  slug: string;
  name: string;
  href: string;
  blurb: string;
  api: string;
  screenshot: string | null;
  crop: 'top' | 'center' | 'bottom';
}

export const formats: readonly LandingFormat[] = [
  {
    slug: 'banner',
    name: 'Banner',
    href: '/formats/banner/',
    blurb: 'Inline rectangular ad anchored inside Compose layout.',
    api: 'BannerAdView(placement)',
    screenshot: null,
    crop: 'bottom',
  },
  {
    slug: 'interstitial',
    name: 'Interstitial',
    href: '/formats/interstitial/',
    blurb: 'Full-screen ad shown at a natural transition point.',
    api: 'adManager.interstitial(placement)',
    screenshot: null,
    crop: 'center',
  },
  {
    slug: 'rewarded',
    name: 'Rewarded',
    href: '/formats/rewarded/',
    blurb: 'Full-screen ad that grants a reward on completion.',
    api: 'adManager.rewarded(placement)',
    screenshot: null,
    crop: 'center',
  },
  {
    slug: 'rewarded-interstitial',
    name: 'Rewarded interstitial',
    href: '/formats/rewarded/#how-is-a-rewarded-interstitial-different',
    blurb: 'Full-screen rewarded ad shown at a natural transition point.',
    api: 'adManager.rewardedInterstitial(placement)',
    screenshot: null,
    crop: 'center',
  },
  {
    slug: 'app-open',
    name: 'App-open',
    href: '/formats/app-open/',
    blurb: 'Full-screen ad shown when the app returns to the foreground.',
    api: 'AppOpenAdCoordinator(manager, controller, config)',
    screenshot: null,
    crop: 'top',
  },
  {
    slug: 'native',
    name: 'Native',
    href: '/formats/native/',
    blurb: 'Compose-rendered ad built from a typed layout DSL and pooled across screens.',
    api: 'NativeAdView(placement, itemKey, layout)',
    screenshot: null,
    crop: 'center',
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
