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
- ROADMAP.md: open items only. BUILDLOG.md: dated entries of what was done and why.
- Read all three at the start of each session and summarise where we left off in 2-3 lines. Update them after each completed step.
- Commit after each working step with a short, clear message. Keep secrets out of code and logs (.env, listed in .gitignore).
- Screenshots and files I send are files too: save each into archive/screenshots/ (non-images: archive/board-files/) named YYYY-MM-DD-HHMM-what-it-shows.ext.

## Szept (my one to-do and reminder list)
Szept holds everything still to do, across all projects. Use the Szept connector (add_reminder, edit_reminder, complete_reminder, cancel_reminder).
- Everything unfinished goes into Szept, dated or not: every open issue, job and decision, and every open ROADMAP item. No date = a to-do (leave due empty); with a date = a timed reminder. Your own jobs go in as undated to-dos starting "Claude: ".
- Every entry gets a priority: urgent (security, or a deadline where something breaks or expires), important (matters, no rush), normal (routine), someday (ideas, maybe-laters). One left without a priority shows as "unsorted" and gets flagged.
- Every entry also gets a slot (how it gets done) and a plan (roughly when). Slots: sat-day (hands-on, Saturday daytime), evening (computer work, Saturday/Sunday evening), work-gap (a short remote job; only if it needs few windows), claude (you do it alone), waiting (blocked). Plan = the Saturday of the weekend to aim for (YYYY-MM-DD), or "later". Spread jobs over weekends, a few hours per slot, urgent first; a due time, if any, still rules.
- Set project to this project's name and add a short opening line (claude_prompt) I can paste to pick the job up with you.
- I can add notes and ask you questions on any Szept item. At the start of a session check list_questions and answer each one with add_comment_reply; read my notes on the items you work on.
- Add the entry in the same turn the issue or ROADMAP line comes up. When it's finished or dropped, complete or cancel the Szept entry in the same turn.
- For this project Szept is the work board too (owner, 22 November 2026). Issues, my jobs, your jobs and decisions are Szept items in project Whisper; use kinds claude, mine and decide, and the Later list for ideas. Every preview build sent to me gets one "mine" item, "Test build <hash> – <what's in it>", with numbered steps in its detail ("You should see: …"; T1 is always "About ends in <hash>"). I tick it Done when all pass, or comment on a step that fails; open a fix item from each failure. Details in docs/HANDOVER.md ("Work tracking").
- When you finish something: complete it with done / tested / not tested / undo. If a Yes needs something from me, leave it open, add a "mine" item with the exact command, and log every command I'm asked to run in a "Commands log" section of the README.
- Whenever an item's state changes, update ROADMAP.md and BUILDLOG.md in the same turn.

## The old review board (frozen)
The preview review sheet, https://claude.ai/artifact/ErrU1RDxYbTrd6nU2rowRk, is read-only history since 22 November 2026: issues 1-132 and build tests up to build 21. Don't write to it or build another board. Old card numbers ("board 56") stay as names in Szept titles, ROADMAP and BUILDLOG.
