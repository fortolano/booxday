# Google verification kit

Everything needed to get BooxDay's Google sign-in verified, so anyone can connect a Google account: no 100-user limit and no "Google hasn't verified this app" warning. Prepared 26 Oct 2026. Nothing here is published or pushed yet.

| File | What it is |
|---|---|
| `site/index.html` | New homepage. Replaces `index.html` on the `gh-pages` branch. |
| `site/privacy/index.html` | Privacy policy. Goes to `privacy/index.html` on the `gh-pages` branch. |
| `permission-reasons.md` | Text to paste in the verification form, one block per permission. |
| `demo-video-script.md` | Shot list for the demo video. |

The existing `openrouter/index.html` page on `gh-pages` stays as it is.

## Step 0: where things stand now

Google Cloud console (https://console.cloud.google.com), the project that holds BooxDay's Android client → **Google Auth Platform**:

- **Audience**: publishing status ("Testing" or "In production"), and the user count against the 100-user cap.
- **Verification Center**: whether verification was ever started.
- **Clients**: the Android client must have package `com.boox.day` and the release key's SHA-1 `9D:FA:F2:D0:9F:63:C7:D3:09:A5:90:6C:A0:28:55:90:E7:DE:BC:2F`. (If Google sign-in works on your tablet with a release or debug build, it's there.)

## Decisions for you

1. **Domain.** Google wants the homepage and privacy policy on a domain you own, checked in Google Search Console.
   - Recommended: buy a domain (about $10 to $15 a year, for example `booxday.app`) and point GitHub Pages at it. Hosting stays free on GitHub. Search Console checks it with a DNS record.
   - Free option: try `fortolano.github.io`. Search Console can check it (with an HTML file), but developers report Google's reviewers rejecting GitHub Pages addresses as "not your domain". If that happens you lose a review round, then buy a domain anyway.
   - If the address changes, the Connect OpenRouter return page (`ProjectInfo.CONNECT_PAGE_URL`) can stay on `fortolano.github.io`. GitHub redirects old `github.io` addresses to the custom domain, so check that page still opens the app after the switch.
2. **App name.** The name on Google's permission screen (Branding page) must match the homepage and the video. The launcher, GitHub and the releases say **BooxDay**; the old site and much of the app's text say "Boox Day". The new pages use **BooxDay**. Pick one and use it on the Branding page.
3. **Support email.** The Branding page needs a "User support email" that users see on the permission screen, and a developer contact email that only Google sees. Pick an address you're happy to show publicly (a separate address for BooxDay is a good idea). The privacy policy uses GitHub issues as the contact, so no email appears on the site unless you want one.
4. **Calendar permission.** Released versions ask for full Google Calendar access ("see, edit, share, and permanently delete all the calendars"), but only read, add, edit and delete events and read the calendar list. Branch `google-verification` narrows it to `calendar.events` + `calendar.calendarlist.readonly`. Release that before recording the video and submitting. Details and evidence: `permission-reasons.md`.

## Steps, in order

1. **Domain** (decision 1). If you buy one: add it to the repository's GitHub Pages settings (this adds a `CNAME` file on `gh-pages`) and set the DNS records GitHub shows.
2. **Publish the pages.** Copy `site/index.html` to `index.html` and `site/privacy/index.html` to `privacy/index.html` on the `gh-pages` branch, commit and push. Check both open in a browser.
3. **Google Search Console** (https://search.google.com/search-console), signed in with an account that is an Owner or Editor of the Cloud project: add the domain and verify it.
4. **Cloud console → Google Auth Platform → Branding:**
   - App name (decision 2), user support email (decision 3).
   - App logo: optional. Leaving it out is fine.
   - App home page: the homepage address. Privacy policy link: the `/privacy/` address.
   - Authorized domains: your domain.
   - Developer contact email.
5. **Data Access:** list exactly the scopes the released app asks for: `openid`, `.../auth/userinfo.email`, `.../auth/userinfo.profile` (basic sign-in), `https://www.googleapis.com/auth/calendar.events`, `https://www.googleapis.com/auth/calendar.calendarlist.readonly`, `https://www.googleapis.com/auth/drive.file`, and for Task Sync `https://www.googleapis.com/auth/tasks` (see "Adding Google Tasks"). Remove anything else listed (including the old full `.../auth/calendar`).
6. **Record the video** (`demo-video-script.md`), upload it to YouTube as Unlisted.
7. **Audience:** if the status is "Testing", tap **Publish app** (In production). Until verification passes, users see the warning and the 100-user cap counts.
8. **Verification Center:** submit. Paste the reasons from `permission-reasons.md` and the video link. In the notes field, say it's an Android app (package `com.boox.day`) that signs in through Google Play services, so there's no browser address bar to show.
9. **Answer Google's email.** Google says sensitive-scope review typically takes 3 to 5 business days, and the Trust & Safety team may email questions first. Reply to that email thread; the review pauses until you answer.

## Adding Google Tasks (Task Sync, Oct 2026)

The calendar review passed on 28 Oct 2026. Task Sync asks for one more permission, `https://www.googleapis.com/auth/tasks`, separately from the calendar sign-in and only when the user turns Task Sync on (`sync/GoogleTasksAuth.kt`). Release builds hide Task Sync until Google approves it (`BuildConfig.TASK_SYNC_ENABLED`). Until then a debug build shows Google's "unverified app" screen for it, which is fine for testing.

1. **Cloud console → APIs & Services → Library:** check that the **Google Tasks API** is enabled for the project.
2. **Google Auth Platform → Data Access:** add `https://www.googleapis.com/auth/tasks` to the scopes. The page shows whether Google counts it as sensitive: if it does, the form asks for the reason in `permission-reasons.md`.
3. **Publish the updated pages** (`site/privacy/index.html`, `site/index.html`) on `gh-pages` and booxday.com, right before submitting: Google reads the live policy. (Ask the owner first.)
4. **Record the Task Sync part of the video** (`demo-video-script.md`, shots T1 to T7). Use a "BooxDay test" list.
5. **Verification Center:** submit the new permission with its reason and the video link.
6. After approval: a small release turns Task Sync on in release builds (`TASK_SYNC_ENABLED` true for release).

## After it passes

- New users no longer see the warning, and the 100-user cap goes away.
- Keep the privacy policy true: if a release starts sending Google data somewhere new, update the policy before shipping it.
- Adding a new Google permission later means another review.
