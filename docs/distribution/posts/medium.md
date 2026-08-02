# Medium — Day 4

**HUMAN STEP — outward-facing publication.** An agent must not publish this.

Medium's rules permit republishing your own content: if you have your own blog
where you publish your content, you may republish it on Medium as long as you
hold the rights. What Medium's spam rules forbid is "repeatedly using responses
or mentions as a method of promotion" — so never comment on other people's posts
to advertise this.

## Use the Import tool, not copy-paste

    https://medium.com/p/import

Paste the **dev.to** URL from Day 2. The import tool backdates the post to the
original date and sets the canonical link automatically, so Medium's domain
authority does not outrank the source. Copy-pasting the text instead produces
an uncanonicalised duplicate — the exact outcome this whole program is trying
to avoid.

Two days after dev.to is deliberate: it gives Google time to crawl the canonical
before the duplicate exists.

## After importing

- [ ] Open the imported story's settings and confirm the canonical link points at
      the dev.to URL. If the import did not set it, set it manually. **If it
      cannot be set, unpublish the story** — an uncanonicalised duplicate is
      worse than no Medium presence at all.
- [ ] Tags (Medium allows five): `Kotlin`, `Kotlin Multiplatform`,
      `Compose Multiplatform`, `iOS`, `Android`.
- [ ] Confirm the fenced code blocks survived the import. Medium's importer
      mangles nested backticks; re-check the Gradle snippets specifically.
- [ ] Confirm the trademark line is present at the end.
- [ ] Do not submit to a Medium publication that requires exclusivity — the
      canonical must stay on dev.to.

The author already has a Medium presence at https://meet-miyani.medium.com,
which the studio site's article feed reads. This post appearing there is a
secondary benefit and is not a reason to skip the canonical.
