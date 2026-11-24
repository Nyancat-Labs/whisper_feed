# Instructions for Claude (this repo)

Project: Whisper. What it is: an Android RSS reader that shows why it placed each article where it did, published by Nyancat Labs on Google Play, F-Droid and GitHub.

## How to work with me
- Be clear and concise. Lead with the answer; no preamble or filler.
- Give one numbered step at a time, say what I should see if it worked, then wait for me to confirm or ask a question before continuing.
- If I start a message with "btw", treat it as a side question: answer it briefly, then restate the step we were on and wait.
- Teach as you go: briefly explain why each step works. Mention useful features, shortcuts or tools I might not know about.
- Challenge me: point out flaws in my reasoning or better approaches, even if I didn't ask.
- When changing code, show only what changes and where, unless I ask for the full file.
- If a request is unclear, ask one short question first. If unsure, say so rather than guessing.
- Ask before deleting files, installing packages, force-pushing, pushing to main, or anything irreversible.
- Exception for this repo: push to main without asking (and keep the session branch in step), but only after the full test suite passes.
- Use Irish/UK English.

## Project documents (top of the repo)
- README.md: what the project does now, main files, how to run it.
- ROADMAP.md: plans, context and ideas (open items live in Szept). BUILDLOG.md: dated entries of what was done and why.
- Read all three at the start of each session and summarise where we left off in 2-3 lines. Update them after each completed step.
- Commit after each working step with a short, clear message. Keep secrets out of code and logs (.env, listed in .gitignore).
- Files that must last live on Szept, not in archive/ (Szept 455): archive/ is gitignored and vanishes with each cloud container. I attach screenshots and files to the relevant Szept item (Claude reads them with get_file); drafts Claude writes for me (emails, replies) go in that item's notes or a comment. Claude has no upload tool, so files I send in the chat get saved to archive/screenshots/ (non-images: archive/files/), named YYYY-MM-DD-HHMM-what-it-shows.ext, for this session only; Claude says so if one matters later.

## Szept (my one list for everything still to do)
Szept (the Szept connector, one page per project) holds every open decision, job of mine, job of yours, part to buy and reminder, across all projects. Szept project for this repo: "Whisper".
- Add an item whenever something comes up (a finding, a follow-up, a command I need to run, a decision, a part to buy), in the same turn. Never leave it only in the chat or in a ROADMAP.
- kind: decide (fill suggest: what Yes means, what you then do, and the undo) | mine (my hands-on job: fill cmd + where = the exact command, where to run it, its side effects and the undo) | claude (yours) | shop (a part to buy) | todo | reminder (timed). Add detail (what and why, with the evidence), area, tags, est_minutes when known, blocked_by or waiting while blocked, and a short claude_prompt I can paste to pick the job up with you.
- priority: urgent (security, or a deadline where something breaks or expires) | important (matters, no rush) | normal (routine) | someday (ideas, maybe-laters). Never leave it empty.
- slot (how) + plan (roughly when): sat-day (hands-on, Saturday daytime) | evening (computer work, Saturday/Sunday evening) | work-gap (a short job from work by phone over Termux, RustDesk or SSH; only if it needs few windows) | claude (you do it alone) | waiting (blocked). Plan = the Saturday of the weekend to aim for (YYYY-MM-DD), or "later". Spread jobs over weekends, a few hours per slot, urgent first; a due time, if any, still rules. Dated weekend jobs go in my batched slots: hands-on Sat 10:00, computer Sat 19:00.
- At the start of each session: list_questions (answer each with add_comment_reply) and project_summary for this project; act on every decision I answered Yes; read my notes on the items you work on.
- When you finish something: complete_reminder with done, tested, not_tested and undo filled in. If a Yes turns out to need something from me: keep it open, add a mine item with the exact command, and set blocked_by. If I say No or want something kept: it's closed; don't suggest it again. If you were wrong about something, say so plainly in a reply on the item.
- Still ask before deleting files, installing packages or anything irreversible, unless the item's suggest described that exact action and I said Yes.
- ROADMAP.md: plans, context and ideas only; on my server Szept writes each project's open items into its "Open in Szept" block every night, so never hand-copy open items. No passwords, PINs or keys in Szept: every chat can read it.
- "Review Szept" (or "review the board") = do the session start and report: done, waiting for me, not started.
- Preview builds (this repo, 22 November 2026): every preview sent to me gets one mine item, "Test build <hash> – <what's in it>", tag build-test, area the build, numbered steps in detail ("You should see: …"; T1 is always "About ends in <hash>"). I complete it when all pass, or comment on a step that fails; open a claude item from each failure. Details in docs/HANDOVER.md ("Work tracking").

## The old review board (frozen)
The preview review sheet, https://claude.ai/artifact/ErrU1RDxYbTrd6nU2rowRk, is read-only history since 22 November 2026: issues 1-132 and build tests up to build 21. Don't write to it or build another board. Old card numbers ("board 56") stay as names in Szept titles, ROADMAP and BUILDLOG.
