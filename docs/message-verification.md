# Message fixes: verification

Use the **Android CI (APK Build)** GitHub Actions workflow. It runs `testDebugUnitTest`
before `assembleDebug` and uploads `ghostgram-test-reports` even when tests fail.
The workflow can also be started manually. No local Java or Gradle setup is required.

Automated regressions cover caption text and UTF-16 formatting offsets, preserving
media metadata through storage, independent bookmarks and deletion for equal message
IDs in different chats, photo completion updates, and migration from database v10.

After CI succeeds, install the APK artifact and check with a signed-in Telegram account:

1. Open a photo-only post and a captioned photo. Both should load without `[Photo]`;
   tapping the image opens the full-screen view. Retry after an interrupted download.
2. Play a music track, voice message, and video inside GhostGram. Check pause, seek,
   completion, and stopping playback when the app goes into the background. Audio
   should yield to another app that takes audio focus. Playback downloads the file
   first; it does not stream partially downloaded files.
3. Check that audio and voice captions remain visible, including bold, italic,
   underlined text, strikethrough, monospace, links, quotes, and tap-to-reveal spoilers.
4. From a second Telegram session, send, edit, delete, and react to a message in an
   imported chat. Watch the open chat and Gazette update without a manual refresh,
   including with GhostGram notifications disabled.
5. Open and save a PDF/document from its file card. Check filename, extension, size,
   download failure, and opening with an installed document viewer.
6. Open a channel discussion, load earlier comments, and receive a new comment.
   Check ordinary, custom-emoji, and paid reaction counts on messages.
7. Search older server-side chat history, change the query quickly, combine a query
   with media filters, and scroll for more results. Search the Gazette and use
   “Load more search results.” Gazette search stays within imported sources, respecting
   the personal-feed setting. The account picker also searches public chats.
8. Review Gazette headlines, serif body text, image captions, and article dividers
   in light/dark mode and at larger font scales. Links and formatting should remain
   correct when the first line is presented as a headline.
9. Install over an existing v10 installation and confirm sources, cached messages,
   and bookmarks survive. Previously overwritten cross-chat messages are recovered
   when their sources refresh.

The app remains read-only. Opening chats and discussions does not call message-read
or outgoing-message APIs. Telegram permissions determine which discussions are accessible;
Android's installed codecs determine which media formats the native players can decode.

## Multiple accounts

The account menu above the feed has **Add account** and a list of saved accounts.
Switching keeps existing Telegram sessions signed in. Each account has its own TDLib
database/files, app database, preferences, selected sources, messages, and bookmarks.
The original account retains the existing storage paths; no data is moved.

Signed-in accounts are monitored by the notification service. Each notification carries
its account identity and switches to that account when tapped. Logging out only signs
out that account. Its cache remains available after signing back into the same account.
An unfinished sign-in can be resumed from the account menu, or you can switch back to
another account at any time. Use Add account for a different phone number.

CI includes `MultiAccountTest` for storage isolation, legacy paths, switching,
independent logout, and unfinished account setup. On a device, additionally check:

1. Add a second account using SMS/Telegram code and, if enabled, its 2FA password.
2. Import different chats into both accounts and confirm feeds, search results,
   attachments, settings, and bookmarks stay separate when switching.
3. Restart the app and confirm it opens the last-selected account without logging
   either account out. Switch away from an unfinished login and resume it.
4. Receive messages in both accounts, including the same group in both. Confirm alerts
   coexist and tapping each opens the corresponding account.
5. Log out of one account, check the other still receives updates, and sign back in.
6. Observe both accounts from a separate Telegram account while switching, reading,
   and playing media. Neither should publish online presence, read receipts, typing,
   or listened/watched acknowledgements. Every account uses the same `online=false`
   initialization; this privacy behavior still requires device verification.
