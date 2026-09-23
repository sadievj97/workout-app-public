# CutCoach: project brief for Claude Code

## What we are building

A personal Android app for one user, installed from Android Studio and never published. It runs the 24-week cut protocol in `protocol.json`:

- shows today's session and the day's timeline,
- guides each workout with timers, animated stick figures, and exercise cue cards,
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
- Compose Canvas for the stick-figure animations, with three-dimensional joints projected in code; no 3D library, no model files, no third-party image or animation assets
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

### Phase 1b: how-to animations and demo loops

Build right after the Phase 1 timers work on the phone.

**Figure animations (drawn in code, work offline)**
- A three-dimensional figure drawn with Compose Canvas: head, torso, both upper and lower arms, both upper and lower legs, hands, feet. No 3D library, no model files, no image files, no third-party animation assets. A rigged human model would be a licensed asset and would break the clean public repo in Phase 6.
- Joints are three-dimensional and projected in code, so one set of poses can be shown from any angle.
- The body is built from those joint centres as rounded solids. Shoulders and hips are placed out to each side along the body's own left-right axis, and every thickness lives in one `body` block in `poses.json`, so a pose only ever describes position, never shape.
- **The muscles are the body.** An `anatomy` block in `poses.json` lists each muscle mass — pectorals, the segments of the rectus abdominis, obliques, lats, trapezius, the three deltoid heads, biceps, triceps, forearm groups, glutes, the quadriceps heads, adductors, hamstrings, calves — as a form riding on a bone, and together they are the silhouette. The frame underneath is drawn slim because the muscles give the shape. Worked muscles go amber, the rest stay mist, which is how an anatomy plate reads.
- Give a muscle a far slimmer rim than a limb. With the same rim, neighbouring masses read as a string of separate pills rather than as anatomy flowing together.
- This is a stylised anatomy diagram, not an écorché render. There are no striations and the contours are smooth approximations. A realistic anatomical model would be a licensed asset.
- Three things make it read as solid: parts are drawn furthest first so nearer limbs cover the ones behind, each part is shaded across its width from a fixed light, and each part carries a dark rim so an arm crossing the chest stays legible.
- Keep a proportion check in the tests. The first attempt had a torso as wide as it was long, which read as a slab and swallowed the head.
- The session player shows the movement as **line art**, large, with a small solid front-facing reference body beside it carrying the worked muscles.
  - The size changed and stuck. The original instruction said the player should show the bare skeleton because a shaded body is a smudge at that size; the observation was right and the conclusion was wrong, because the answer was to stop drawing it at that size. The owner opened a session, read "Pull-up, set 1 of 4", and did not know how to do it, which is the question the player exists to answer.
  - The shading changed and was changed back. Drawn solid at 200dp it was legible and the owner found it ugly — the shaded body is a stack of rounded masses and at a joint they pile into blobs. For something looked at every morning that is the only test that matters. Line art is a diagram, and a diagram is what someone copying a movement wants.
  - The reference body stays solid because it is the one thing that must show *muscles* rather than limbs, and the line renderer only knows limb groups. It lives in the `anatomy` block of `poses.json`, because an exercise's own camera is chosen to show the movement and is regularly the wrong angle for its muscles.
- Each exercise is a short sequence of poses (keyframes) with smooth interpolation between them.
- Store the poses as data in `app/src/main/assets/poses.json`, one entry per exercise id in `exercises.json`, so a wrong pose can be fixed without touching drawing code.
- Warm-up movements live in a separate `warmup` block of the same file, not under `exercises`: they have no ladder, no sets and no muscles, and a test asserts every entry under `exercises` is a real exercise. A warm-up block in `exercises.json` names the movements it shows, because one block can name several — "hip circles, leg swings, arm circles, cat-cow" is four things, and one animation of all four would be a lie. The player shows them in turn.
- Some movements cannot honestly be drawn by this skeleton, and are named in words with no figure rather than approximated. Cat-cow needs a spine joint the rigid torso does not have: solving for planted knees slides the hand about 10 cm across the floor between the two positions. Hands on hips is impossible for the same class of reason — the arm reaches 0.2095 from the neck and the torso is 0.259 long. `poses.json` records both, so neither gets re-attempted.
- Most exercises are symmetrical, so write down one arm and one leg and reflect the other. Write both out only where the sides genuinely differ, as in a split squat.
- Draw props as simple shapes: floor plane, pull-up bar, chair, table edge, step, towel. They live in the same world as the figure and turn with the camera.
- Each exercise names its own camera in `poses.json`, as an azimuth and an elevation in degrees, chosen to show that movement clearly. Dragging the figure turns the camera; a control returns it to the chosen angle.
- Tempo follows the current ladder step:
  - Default: about 1 second up and 1 second down.
  - On a "3-second lowering" step, the lowering takes 3 seconds.
  - On a "pause" step, the figure holds at the hardest point for the time the ladder text states, or 1 second if it states none.
  - Timed holds show the held position with a countdown ring.
- Each-side exercises: animate one side, and mirror the figure for the other side and during "Switch sides".
- Placement: small and glanceable next to the set counter in the session player; large at the top of the cue card.
- Styling: the figure in `TextHigh`, with the worked muscles highlighted in `Ember` during work phases. Named, not hard-coded: these were the old navy-scheme hexes and went stale the moment the palette changed. `ui/theme/Color.kt` is the source of truth, exactly as `protocol.json` is for training numbers.
- **Worked muscles:** `exercises.json` already names the muscles per exercise, so each form in the `anatomy` block lists which of those names it answers to. Sides are relative to the body, so each exercise declares a `facing` direction; without it "the front of the arm" has no meaning when you are lying face down.
- A muscle on the far side is hidden by the body in front of it, which is what an anatomy figure does. The legend names it either way.
- Name the highlighted muscles in a legend beside the figure, and keep a test that fails if any muscle in `exercises.json` has no form. The shading is schematic, not anatomical, and the cue card says so.
- Never imply that working a muscle reduces fat over it. See the `honest_core_expectations` guardrail.
- Reduced motion: if the phone's "remove animations" setting is on, show the start and end poses side by side instead.

**Accuracy review**
- Build the engine and three exercises first (push-up, pull-up, hollow hold), then stop for the owner's approval of the look before animating the other twelve.
- Add a Form check screen listing all 15 animations. Each gets "Looks right" and "Needs fixing" buttons and a note field. Until the owner marks an animation "Looks right", it shows a small "Unreviewed" label.
- The figures are schematic reminders of movement and tempo. They do not replace learning form from a video or a coach; the cue card says so once.
- After all 15 base animations are approved, add variant poses for ladder steps that change body position (feet elevated, knees bent, single leg, arms overhead). Steps that change only tempo reuse the base pose.

**Looping demo clips (online, in the cue card)**
- Optional `video_start_s` and `video_end_s` fields on each exercise, editable in Settings next to the video link. A whole demo video is mostly talking; the loop is for the rep.
- When `video_url` is set, play that segment muted and on loop inside the cue card through YouTube's official embedded player (IFrame Player API). Loop by seeking back to `video_start_s` when playback reaches `video_end_s`.
- Never download, cache, or re-host video files.
- With no internet, or if a video does not allow embedding, fall back to the stick figure plus an "Open in YouTube" button.

**Checks before Phase 2**
- A unit test fails if any exercise in `protocol.json` has no pose.
- On a "3-second lowering" step, the lowering phase lasts 3 seconds, within a small tolerance.
- No limb changes length between the poses of one exercise, or part way through a rep.
- Every figure stays inside its frame at its own camera angle and when turned right around.
- Mirroring works for every each-side exercise.
- The reduced-motion fallback works.
- The whole session player works in airplane mode.

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
- The stick figures are original work and can ship in the public repo. Demo videos stay as links, never copies.

## Design direction

The app is used half-awake at 05:35 in a dim room, with the phone on the floor and one hand free.

The first palette was navy and amber, and it read as a utility: every screen was a stack of identical grey panels of label-and-value rows, so nothing could be found at a glance. Replaced September 2026 at the owner's request. The constraints below survived the change; the colours did not.

- **Dark, and only dark.** No light variant. The room is dim and a second scheme is a second thing to keep correct.
- **Spend boldness in one place per screen.** In the player that is the timer: huge tabular numerals, plus a phase glow filling the top of the screen, because a coloured numeral alone is not readable from across the floor. On Today it is the session card.
- **Warm means work, cool means rest.** The values changed; the mapping is load-bearing and did not.
- **Palette** (`ui/theme/Color.kt` is the source of truth; these are the names):
  - `Ink` #0B0A12 background — near-black with a violet cast, because violet sits under the whole accent range and a blue-navy base fights orange
  - `InkLift` #13111F for the tab bar and sheets
  - `Surface` white at 6% — translucent, so a card over artwork picks it up instead of punching a hole in it
  - `TextHigh` / `TextMid` / `TextLow` for the three levels of text
  - `Ember` #FF6B35 work · `Sky` #38BDF8 rest · `Mint` #34D399 met · `Amber` #FBBF24 attention · `Accent` #A855F7 primary action
- **Every session has its own gradient**, derived in `ui/theme/SessionArt.kt` from its id and `hr_zone` — structural fields, not its name, which is prose and lies. A test fails if two sessions collide.
- **Imagery is generated, never bundled.** Gradients and the 3D anatomy figures are the artwork. This is what keeps the licensing promise below intact.
- **Type:** Atkinson Hyperlegible throughout, chosen for legibility when tired; tabular figures for every number.
- **Words and controls:** sentence case, plain verbs, no decoration. Touch targets of at least 56 dp for logging reps.

## Security, privacy, and licensing

- Never bundle copyrighted images, GIFs, or video files. Figures are drawn in code; demo videos play only through YouTube's own player.
- No secrets in code or git. Keep local config in `local.properties`, and list it in `.gitignore`.
- Health data stays on the phone, except the minimal summaries sent to Claude in Phase 4.
- The app is never distributed. It is installed from Android Studio on the owner's phone only.

## Reference docs

- Health Connect, getting started: https://developer.android.com/health-and-fitness/guides/health-connect/get-started
- Health Connect, reading data (30-day limit and history permission): https://developer.android.com/health-and-fitness/health-connect/read-data
