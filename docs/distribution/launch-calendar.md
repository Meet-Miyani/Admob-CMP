# Launch calendar

**Every posting step below is performed by a human.** No agent posts to a
community forum, Slack workspace, subreddit, blogging platform, or mailing list
on this project's behalf, under any circumstance. Agents write the copy into
`docs/distribution/posts/` and stop.

Day 0 is the day `https://ads.avinya.dev` is live, its sitemap is submitted to
Google Search Console (Plan 2), and the guide pages named in the copy exist
(Plan 3). Do not start before all three are true. Posting a link to a docs site
that 404s in places is the one mistake none of these communities forgive.

## Per-community rules — researched 2026-07-31

| Channel | Rule as found | What this plan does |
|---|---|---|
| Kotlin Slack, all channels | *"Please refrain from cross-posting the same message on multiple channels. It is considered spamming."* — https://kotlinlang.org/docs/slack-code-of-conduct.html | **Three different messages in three channels, staggered over three weeks. Never the same text twice.** |
| Kotlin Slack `#feed` | The announcements/links channel. Moderated by Nicola Corti (@gammax) and Youssef Shoaib. | The release announcement goes here, and only here. |
| Kotlin Slack `#multiplatform` | Moderated by Andrey Mischenko (@gildor). No published channel-specific rules; the CoC says moderators keep channel rules in the channel topic. | Read the topic first. Post a tooling-specific message about Kotlin/Native test linking — genuinely different content, not a copy. |
| Kotlin Slack `#compose` | Moderated by Maryam Alhuthayfi and Zach Klippenstein. | Read the topic first. Post a Compose-specific message about the native-ad layout DSL — again, different content. |
| Kotlin Slack, general | `@channel` and `@here` are disabled. Do not ping maintainers to get attention. Use code blocks, one message, no thread-splitting. | Copy is a single message with one fenced block. |
| r/Kotlin | **Rules could not be read programmatically — Reddit blocks the tooling used for this research.** They must be read by a human before posting. | Hard gate: read the rules, then post. Copy is written as a self-post so it survives the strictest plausible ruleset. |
| dev.to | The Code of Conduct contains no self-promotion prohibition. Cross-posting is supported first-class via the `canonical_url` front-matter field. | Full article, `canonical_url` pointing at the docs page. |
| Medium | Cross-posting your own content is allowed if you hold the rights. The Import tool backdates the post and sets the canonical link automatically. Spam rules forbid "repeatedly using responses or mentions as a method of promotion". | Import the dev.to article. Never promote via comments on other people's posts. |
| Kotlin Weekly | Submissions explicitly invited: `mailto:mailinglist@kotlinweekly.net` with subject `Link for submission - Kotlin Weekly`. Roughly 23,000 subscribers. Editorially curated, so there is no spam risk in submitting. | Submit on Day 1. Highest value-per-risk channel in this list. |
| "Compose Multiplatform community channels" | **There is no official Compose Multiplatform forum.** `JetBrains/compose-multiplatform` has GitHub Discussions **disabled** and `/discussions` returns 404. | The CMP community is Kotlin Slack `#compose`, `#compose-ios`, `#compose-desktop`. Covered by the Slack row. Do not invent a channel. |
| discuss.kotlinlang.org → **Libraries** | Category description scopes it to *"the Kotlin standard library and other **kotlinx** libraries"*. A third-party library announcement is off-topic there. | **Excluded.** Do not post in Libraries. |
| discuss.kotlinlang.org → **Multiplatform** | A general discussion category (359 topics), not an announcement board. | Optional, low priority, after Day 30 and only if there is a question to answer rather than a thing to announce. |

## Sequencing, and why it is this order

The ordering is not arbitrary. It moves from channels this project controls, to
editorially-mediated channels, to community channels where a post is judged by
strangers — so that by the time a stranger clicks through, there is a docs site,
a real article, and a release page waiting rather than a bare repo.

| Day | Channel | Action | Who |
|---|---|---|---|
| 0 | Owned | The rewritten 1.1.0 release body is live (Task 5) | Human |
| 0 | Owned | `avinya.dev/open-source/` featured card is deployed (Task 4) | Human |
| 1 | Kotlin Weekly | Submit the docs-site link by email | Human |
| 2 | dev.to | Publish the article with `canonical_url` set | Human |
| 4 | Medium | Import the dev.to article (auto-canonical) | Human |
| 5 | Kotlin Slack `#feed` | Single release announcement | Human |
| 7 | r/Kotlin | Self-post — **only after reading the subreddit rules** | Human |
| 14 | Kotlin Slack `#multiplatform` | Kotlin/Native test-linking message (different content) | Human |
| 21 | Kotlin Slack `#compose` | Native-ad layout DSL message (different content) | Human |
| 30 | — | 30-day metrics checkpoint (Task 7) | Human |
| 90 | — | 90-day metrics checkpoint + `kmp-awesome` gate re-check (Tasks 2, 7) | Human |

Medium trails dev.to by two days on purpose: dev.to is the canonical, and giving
Google a couple of days to crawl the canonical before the duplicate appears is
the cheapest possible insurance on a brand-new host.

r/Kotlin trails the article by five days on purpose: a Reddit post that links to
a written explanation reads as sharing something, and a post that links to a bare
repo reads as advertising. The former survives moderation; the latter is what
gets removed.

## Rules that apply everywhere

- Every post carries the trademark line: *Not affiliated with or endorsed by
  Google. AdMob and Google Mobile Ads are trademarks of Google LLC.*
- Never name a competing library in a post. Spec §6 makes comparison content a
  neutral capability matrix on our own site, not a talking point in someone
  else's community.
- Never claim ranking, download, or adoption numbers. There are none yet.
- Disclose authorship in the first or second sentence, everywhere. Every channel
  below is fine with an author sharing their own work and hostile to an author
  pretending not to be one.
- If a moderator asks for a change, make it and do not argue.
