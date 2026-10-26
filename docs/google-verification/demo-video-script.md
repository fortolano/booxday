# Demo video script (Google OAuth verification)

Google wants a video that shows the whole sign-in, Google's permission screen, and how the app uses each permission. Upload it to YouTube as **Unlisted** and paste the link in the verification form.

Target length: 3 to 4 minutes. No music needed. Narration is optional; on-screen captions or a typed note at the start are enough. Speak or caption in English.

## Before recording

1. **Tablet language: English.** Google's permission screen must be in English in the video (Android Settings → Language).
2. **Use a test Google account**, not your personal one. Add 2 or 3 events to its calendar first (on the web) so the sync has something to show.
3. **Make Google ask again.** If this account has used BooxDay before, Google skips the permission screen. Remove the account in BooxDay (Settings gear → Calendar Accounts → remove the account; removing the last Google account also withdraws BooxDay's access), or remove BooxDay at https://myaccount.google.com/permissions.
4. **Same name everywhere.** The app name on Google's permission screen comes from the Cloud console (Branding page). It must match the name on the homepage and in this video (see README, "App name").
5. **Have a computer or phone ready** with https://calendar.google.com and https://drive.google.com open on the test account. Showing the same event there is the clearest proof of what the app does.
6. **Recording.** Either film the tablet with a phone (simple, works on e-ink), or record the tablet's screen from the PC:
   - `adb shell screenrecord --time-limit 180 /sdcard/Movies/booxday-demo-1.mp4` (3 minutes per file; record part 2 with `-2.mp4`), then `adb pull` the files and join them.
   - Do a 10-second test first: BOOX screens sometimes record black or very slowly. If so, film with a phone instead.
   - Hide notifications that show personal data.

## Shot list

| # | Show | Caption / narration |
|---|---|---|
| 1 | Title card (or a typed note on screen): "BooxDay, Android app for BOOX e-ink tablets. Package com.boox.day. Google sign-in through Google Play services, so there is no browser address bar." | "This video shows how BooxDay signs in with Google and how it uses each permission." |
| 2 | Android Settings → Apps → BooxDay (App info), so the installed app and its name are visible. Then open BooxDay. | "BooxDay installed on a BOOX tablet." |
| 3 | In BooxDay: tap the **Settings** gear at the top of the calendar → **Calendar Accounts**. | "The user connects a Google account from Calendar Accounts." |
| 4 | Tap **Add Gmail Account**. Pick the test account in Google's account chooser. | |
| 5 | **Google's permission screen.** Hold still for 5 seconds on: the app name, the account, and each permission listed ("View and edit events on all your calendars", "See the list of Google calendars you're subscribed to", the Drive files BooxDay creates, email address). Record with a build that has the narrowed permissions (branch `google-verification`), not 1.0.37. Scroll if the list is long. Then tap **Allow** / **Continue**. If Google shows the "unverified app" warning first, show it and continue through it. | "Google's consent screen shows the app name BooxDay and the permissions it asks for." |
| 6 | Back in BooxDay: the account appears with its list of calendars. Turn on one or two calendars, tap **Sync All Now**, then **Done**. | "Calendar list: the user picks which calendars to show." |
| 7 | The month calendar now shows dots on days with events. Tap a day: the event panel lists the Google events. Show the same events in Google Calendar on the computer. | "Reading events: BooxDay shows the user's Google Calendar events on the tablet." |
| 8 | Tap **+** (Add event) in BooxDay. Title "Verification demo", pick a time, under **Save to** choose **Cloud Calendar** → **Google** and the test calendar, tap **Save**. Refresh Google Calendar on the computer: the event is there. | "Creating events: events the user adds in BooxDay are saved to their Google Calendar." |
| 9 | Open the event in BooxDay, change the time, **Save**. Refresh Google Calendar: the time changed. | "Editing events." |
| 10 | Open the event again → **Delete Synced Event** / **Delete Event**, confirm. Refresh Google Calendar: it's gone. | "Deleting events. This is why BooxDay needs edit and delete access, not read-only." |
| 11 | Settings gear → **Data & Backup** → **Google Drive** → **Backup Now**. Then show drive.google.com: the BooxDay backup folder and the new file. | "Google Drive: BooxDay only creates and reads its own backup files. It can't see other Drive files." |
| 12 | In Data & Backup, open the restore list to show BooxDay listing its own backups. (No need to restore.) | "Restore reads back only BooxDay's own backup files." |
| 13 | Settings gear → **Calendar Accounts** → remove the Google account. | "The user can disconnect at any time. BooxDay deletes the events it downloaded for that account and, for the last Google account, withdraws its access." |
| 14 | End card: homepage address and privacy policy address. | |

## Check before uploading

- The permission screen is readable and in English, with the app name visible.
- Every permission you request is shown being used: calendar (read, create, edit, delete) and Drive backup.
- No personal emails, names or notifications other than the test account.
- The app name in the video matches the Cloud console and the homepage.
