# Chat interface refinement

Channels use wide post surfaces; group and personal conversations use aligned,
responsive bubbles. Nearby messages from the same sender share a visual group,
with local-calendar date separators. Date labels live inside the existing message
list items so notification jumps, quoted-reply jumps and history paging keep their
message indices. The Gazette continues to use its article layout.

Channel discussions use a Material modal sheet with an original-post preview,
shared message styling, and distinct loading, empty, error and retry states.
Refreshing merges current replies into loaded history instead of discarding it.
Comment menus omit bookmarking because the discussion view has no bookmark action.

## Accessibility fixes

| Previous issue | Relevant guideline | Priority | Change |
| --- | --- | --- | --- |
| 16 dp unlabeled message icons | Android 48 dp targets; WCAG 2.5.8 and 4.1.2 | High | Labeled 48 dp menu control and named menu actions |
| Faint 9 sp timestamps | WCAG 1.4.3 | High | Standard label typography and contrast-tested text colors |
| Fixed-width bubbles and cramped headers | WCAG 1.4.4 and 1.4.10 | High | Responsive widths, wrapping metadata, scalable text and scrollable sheets |
| Inline links without link semantics | WCAG 2.1.1 and 4.1.2 | High | Compose LinkAnnotation links, focus styling, selectable text |
| Visually hidden spoilers still in accessible text | WCAG 1.3.1 | High | Mask hidden text, expose a reveal link and a spoken description |
| Discussion refresh replaces older comments | Usability and reading continuity | Medium | Merge updated messages using chat/message identity |
| Search and filter close icons have no labels | WCAG 4.1.2 | Medium | Explicit clear-search and clear-filter descriptions |

`ChatPresentationTest` covers sender grouping, timezone boundaries, history merging
and normal-text contrast in both themes. `ChatUiTest` checks message/menu actions,
touch targets, large text, discussion retry/dismissal and hidden spoiler semantics.
It writes native Android previews to `app/build/reports/chat-previews`; CI includes
them in the `ghostgram-test-reports` artifact.

## Device verification

1. Read channel, group, private-chat and saved-message history in light and dark
   mode at normal and 200% system text size. Repeat on a narrow screen and in
   landscape. Titles and metadata should wrap; media and menu controls should remain
   reachable. Also exercise the app's independent text-size setting.
2. With TalkBack, traverse the header, message body, links, original-reply preview,
   timestamp, options menu and discussion action. Verify useful labels, focus order,
   and that spoilers stay hidden until activated. Check keyboard navigation too.
3. Use search, clear the query, change shared-media filters and clear the filter.
   Confirm Back dismisses search before leaving the conversation.
4. Open a channel discussion, load older comments, then receive and edit replies.
   Older loaded history should remain. Delete a reply from another session, retry
   an offline load, and dismiss using Close, Back and the sheet gesture.
5. Test quoted replies, notification targets, first/latest navigation and scrolling
   across midnight. Date separators must not shift message-target indices.
6. Play audio, scroll away, and reopen a discussion. The existing background audio
   service and notification controls should continue to work.

Native render tests supplement device checks; they do not establish full TalkBack
or WCAG conformance.
