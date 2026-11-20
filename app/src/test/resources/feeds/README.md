# Problem feeds, kept as samples

Shapes of real feeds that once broke Whisper, cut down to what matters, so a
later change that breaks them again fails FeedFixturesTest instead of
turning up on a phone. Article text is placeholder; the structure is the
point. Add one whenever a feed misbehaves.

| File | Site | What it tests | Board |
|---|---|---|---|
| darknet-trailing-script.xml | darknet.org.uk | a caching plugin's `<script>` after `</rss>` | 117 |
| quartz-empty.xml | qz.com | a feed that answers with no items (the exact body Quartz sent wasn't kept; this is the shape the parser saw) | 111, 128 |
| cyberspace-markdown.xml | cyberspace.online | Markdown, not HTML, in each item: a photo, bold, an ASCII-art code block | 45 |

IT Security Guru (a 403 to apps) is a refusal, not a body, so it's covered
in FeedFixturesTest by the error it produces.
