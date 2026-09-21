# CutCoach: project brief for Claude Code

## What we are building

A personal Android app for one user, installed from Android Studio and never published. It runs the 24-week cut protocol in `protocol.json`:

- shows today's session and the day's timeline,
- guides each workout with timers and exercise cue cards,
- logs every set,
- reads Galaxy Watch 8 data through Health Connect,
- recommends progressions and substitutions that follow the protocol's own rules.

Later, Claude adds substitutions, harder variations, and weekly reviews.

## Who you are working with

- The owner is not a software engineer: intermediate Python, advanced R, new to Android and Kotlin.
- Before writing code for any step, propose a short plan and wait for approval.
- Work in small steps. After each step, explain in plain language what changed, then give exact click-by-click instructions to run it on the phone.
- When something fails, explain the cause in one paragraph before fixing it.
- After each phase, explain the three most important files in plain language, so the owner can describe the architecture in an interview.
- Commit to git after every working step with a clear message.

## Source of truth

- `protocol.json`: schedule, sessions, targets, rules, guardrails, open decisions. Never hardcode a number that lives in this file; read it from the file.
- `exercises.json`: exercise library, cues, progression ladders, warm-up.
- Both files ship in `app/src/main/assets/` and load at startup.
- Resolve every item in `open_decisions` with the owner before building a feature that depends on it, then write the answer back into `protocol.json`.
- Items under `proposed_rules_needing_approval` are not active until the owner approves them.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Room for local storage; all data stays on the phone
- WorkManager for the daily Health Connect sync
- Health Connect Jetpack client (`androidx.health.connect:connect-client`) from Phase 2
- minSdk 28; targetSdk the latest stable version
- No backend, no accounts, no analytics, no cloud sync (Phase 4 adds one small proxy, described below)

## Phases

Build in order. Do not start a phase until the previous one works on the phone.

### Phase 1: offline core

**Today screen**
- Today's session from `weekly_schedule`.
- The daily timeline.
- Steps and nutrition targets shown as reminders only.

**Session player**
- Warm-up first, then each exercise with its sets, target, and a log field per set.
- Rep sets: tap to log reps, and the rest timer starts automatically.
- Timed holds: a countdown for the hold, then rest.
- Each-side exercises: first side, then a "Switch sides" countdown, then the second side.
- A session clock that gently flags when the session runs past its planned duration.
- Deload weeks: show the reduced set count (every 4th program week, sets times 2/3, rounded).

**Exercise cue card**
- Setup, cues, common mistakes, easier version, and the current ladder step.
- A button that opens `video_url` in YouTube when set.

**Timers**
- Keep the screen on during a session.
- At zero, vibrate and play a short tone.
- Large tabular digits readable at arm's length.

**Other screens**
- History: past sessions per exercise.
- Body tracking: daily weight entry with a 7-day average; waist entry on Sundays.
- Settings: program start date, ramp start date, rest default, video links, rehab exercise list.
- Weekly view.

### Phase 2: watch data through Health Connect (read only)

Samsung Health syncs to Health Connect, and the app reads from Health Connect.

**Record types**
- StepsRecord, SleepSessionRecord, HeartRateRecord, RestingHeartRateRecord, HeartRateVariabilityRmssdRecord, ExerciseSessionRecord, WeightRecord.
- Verify the exact permission strings in the official documentation before declaring them.

**Required setup**
- The privacy-policy rationale activity: handle ACTION_SHOW_PERMISSIONS_RATIONALE, plus the Android 14+ activity-alias for VIEW_PERMISSION_USAGE. Without it, the permission flow does not work.
- Request the history permission, because reads are limited to 30 days before the first grant without it.
- Request the background read permission, so the daily sync works without opening the app.

**Data Check screen (build first)**
- For each record type, show how many records exist in the last 30 days and the latest value.
- This shows what the phone actually provides before anything depends on it.

**Derived metrics**
- Last night's sleep start and end versus the bedtime-ramp target for that night.
- Sleep duration.
- Sleeping heart rate: the average heart rate during the main sleep session.
- Steps versus the daily target.
- Weight 7-day average: merge manual entries and WeightRecord, preferring manual entries.

**Session heart rate**
- On cardio days the owner records the workout on the watch.
- The app matches the ExerciseSessionRecord by time and reports time in Zone 2 or in the interval zone.

### Phase 3: rules engine and recommendations (no AI)

- Pure Kotlin functions. Each reads its numbers from `protocol.json` and has unit tests.
- **Progression:** per exercise, compare the last session with the target and suggest more reps, the next ladder step, or staying put.
- **Deload:** every 4th program week, sets times 2/3, rounded (4 becomes 3; 3 becomes 2).
- **Plateau:** apply the plateau rule once "flat" is defined. Suggest exactly one change, then block further changes for 3 weeks.
- **Other rules:** bedtime-ramp adherence, sleeping heart rate trend versus goal, heart rate zone feedback per session, the fast-loss flag, and the week-1 scale rule.
- **Readiness adjustment:** only if approved in `open_decisions`.
- Every recommendation shows a one-line reason, for example: "All 4 sets hit 15 last Monday, so try feet elevated at 8 reps."
- Enforce every guardrail in `protocol.json` in code.

**Rule tests (write these before the rules)**
1. Push-up sets 15, 15, 15, 15: advance one ladder step, reps restart at 8.
2. Push-up sets 15, 15, 14, 12: stay on the step, add reps.
3. Program week 4: every exercise's sets are cut by a third.
4. Week 1: no weight verdict is shown.
5. Three flat weeks: exactly one change is suggested, and no second change for 3 weeks.
6. The 7-day average falls 0.9 kg per week for two weeks: fast-loss flag, and no further cut is suggested.
7. Zone 2 day with an average heart rate of 150: "above Zone 2" feedback.
8. Sleep starts at 23:30 on ramp night 2 (target 22:45): "45 minutes late".
9. Watch calories are never shown anywhere as food to eat back.
10. Rehab exercises are never changed by any recommendation.
11. Pain report: no workout is suggested, and clinician advice is shown.
12. Missing sleep data: a check-in question, not a guess.

### Phase 4: Claude layer

**Proxy and key handling**
- All calls go through a Cloudflare Worker proxy that holds the Anthropic API key as a secret.
- The app authenticates to the Worker with a shared-secret header.
- The API key never appears in the app, in git, or in logs.

**Features**
- **Substitute:** "no bar today", "hotel room", or "knee sore" gets a swap within the same movement pattern from the exercise library. Pain gets rest plus clinician advice instead.
- **Harder variation:** when a ladder tops out (for example pike push-ups), suggest a harder variation in the same pattern.
- **Weekly review:** a plain-language summary of sleep-ramp adherence, sleeping heart rate, steps, weight trend, and strength progress, with at most one change that follows the plateau rule.

**Rules for every call**
- Send only the summaries a request needs (for example 14 days of daily aggregates), never raw health records.
- Claude returns JSON that matches a schema. The rules engine validates it against the guardrails and rejects anything that adds volume, touches rehab, breaks the calorie floor, or suggests a lie-in.
- Models: `claude-sonnet-5` for the weekly review, `claude-haiku-4-5` for quick substitutions.
- Log the prompt version, model, and token counts for every call.

### Phase 5 (optional): Samsung Health Data SDK

- Adds Energy Score, which is not available through Health Connect, as a readiness input.
- Use the SDK in developer mode: read only, personal use. Distribution would require a Samsung partnership.
- Read Samsung's docs first and give the owner the manual phone steps:
  - https://developer.samsung.com/health/data/overview.html
  - https://developer.samsung.com/health/data/process.html

### Phase 6: portfolio

- README covering the problem, architecture, key decisions, test results, and what failed.
- Sample data only. Confirm that no secrets and no personal health data exist anywhere in the git history.

## Design direction

The app is used half-awake at 05:35 in a dim room, with the phone on the floor and one hand free.

- **Spend boldness in one place: the timer.** Huge tabular numerals; the phase (work, rest, switch sides) shown by color plus a one-word label.
- **Palette:**
  - Pre-dawn navy #101B2D background
  - Slate #1D2B42 surfaces
  - Mist #D5DDE8 text
  - Sunrise amber #F2A541 for work phases
  - Cool sky #7FB2E5 for rest
  - Dark by default.
- **Type:** Atkinson Hyperlegible throughout, chosen for legibility when tired; tabular figures for every number.
- **Words and controls:** sentence case, plain verbs, no decoration. Touch targets of at least 56 dp for logging reps.

## Security and privacy

- No secrets in code or git. Keep local config in `local.properties`, and list it in `.gitignore`.
- Health data stays on the phone, except the minimal summaries sent to Claude in Phase 4.
- The app is never distributed. It is installed from Android Studio on the owner's phone only.

## Reference docs

- Health Connect, getting started: https://developer.android.com/health-and-fitness/guides/health-connect/get-started
- Health Connect, reading data (30-day limit and history permission): https://developer.android.com/health-and-fitness/health-connect/read-data
