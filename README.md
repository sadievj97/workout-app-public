# CutCoach

An Android app that runs one person's 24-week cut: it shows the day's session,
guides it with timers and animated figures, reads a Galaxy Watch through Health
Connect, and suggests progressions that follow the protocol's own rules.

Kotlin, Jetpack Compose, Room. **215 unit tests, no instrumentation.** All health
data stays on the phone.

> The protocol in this repository is **sample data**. The real one is a
> gitignored `protocol.local.json` — a start weight, heart-rate zones and a
> bedtime are health data about a person.

---

## The problem

Most fitness apps are a log. You do a thing, you write it down, and the app
stores it. The judgement stays with you.

This one starts from a written protocol — a 24-week cut with rules about
progression, deloads, plateaus, calorie floors and sleep — and asks what an app
would have to be to *apply* those rules rather than just record their outcome.

Three constraints shaped every decision:

**It is used half-awake at 05:35**, phone on the floor, one hand free. Timers are
huge and tabular; touch targets are 56 dp; the palette is dark by default.

**The protocol is the source of truth, not the code.** Every number — the deload
multiplier, the bedtime ramp, the calorie floor, how short a sleep counts as a
nap — lives in `protocol.json`. Nothing is hardcoded. Changing the program is
editing a file.

**Health data does not leave the phone**, except the minimal daily summaries the
Claude layer sends, and only when asked.

---

## Architecture

```
protocol.json  exercises.json  poses.json      ← the source of truth
        │
        ▼
:core   plain Kotlin. No Android on the dependency path at all.
        ├── protocol/   the two JSON files, parsed
        ├── session/    the ordered plan a session is walked through
        ├── rules/      the engine: progression, deload, plateau, readiness
        └── health/     the derived metrics and the types they produce

:app    Android: Compose, Room, Health Connect, WorkManager
        ├── ui/         every screen; figures drawn in code
        ├── health/     Health Connect reads and the daily sync
        ├── claude/     summaries → Worker → Claude → guardrails
        ├── samsung/    optional SDK, its own source root
        └── data/       Room, and the figure geometry
```

**The rules engine is pure Kotlin with no Android in it.** That is the single
most load-bearing decision in the project: it lets the twelve rule tests the
brief specifies run on a laptop in seconds instead of needing a device. Gathering
the data — database, settings, watch — lives in a separate file so the rules stay
testable.

It is now a module rather than a habit. Of the 1,700 lines that moved into
`:core`, exactly one file imported anything from Android, and only to read a
file by name — so `ProtocolRepository` asks for the text of a named file and
`:app` answers with its assets. The discipline was already being kept; the
module makes it impossible to break, because a `Context` is not on the
dependency path to reach for. It is also what would let a second front end —
a watch, say — build the same session from the same protocol without
reimplementing any of it.

**Nulls are load-bearing.** A missing value means the watch recorded nothing, and
nothing in the app ever turns that into a zero. A night with no data is not a
night of no sleep. The protocol has a guardrail for it (`no_guessing`), and it is
enforced as a type signature rather than a convention.

**The figures are drawn in code.** Fifteen exercises, each a set of 3D joint
positions in `poses.json`, projected orthographically onto a Compose Canvas, with
muscle masses riding on the bones. No model files, no image assets, no licensing
question. Every ladder rung that changes body position has its own geometry —
69 rungs covered.

**Claude returns typed JSON, never prose.** "Eat 300 fewer calories" in a sentence
is unenforceable; `caloriesKcalChange = -300` is a number to compare against the
floor. The rules engine validates every answer and shows refusals rather than
hiding them.

---

## Key decisions

**The protocol file over configuration in code.** Numbers drift when they live in
two places. When the weight target changed, three other numbers in the file
stopped agreeing with it — and because they were all in one file, that was
visible and could be written down rather than silently wrong.

**Guardrails in code, not in the prompt.** The model is told the rules, which
makes good answers likelier. Only the validator makes bad ones impossible. An
answer that adds a set, drops leg work, or suggests a lie-in is rejected with
every rule it broke named — not the first one, because fixing one would leave it
invalid.

**The API key never enters the app.** An Android app is a zip file anyone can
unzip. The Anthropic key lives only as a Cloudflare Worker secret; the app
carries a different shared secret that rotates with one command.

**Check what the device actually provides before building on it.** Twice: a Data
Check screen before the Health Connect features, and an Energy Score probe before
the Samsung integration. Both changed what was worth building.

**Tests that render.** A test writes an SVG contact sheet of every pose using the
real projection code, and another writes every figure's limb lengths. Neither
asserts much. They exist to be looked at, and they caught more real bugs than the
assertions did.

---

## Test results

```
215 tests, 0 failures  (87 in :core, 128 in :app)
```

| Area | What it covers |
|---|---|
| `ProtocolLoadTest` | the shipped JSON through the real parser |
| `RuleTest` | the twelve rule cases the brief names, written first |
| `GuardrailTest` | adversarial: does a *plausible* bad answer get through? |
| `PoseTest` | limb lengths, frame bounds, mirroring, variant coverage |
| `HealthMetricsTest` | fragmented nights, missing heart rate, capped gaps |
| `LadderTest`, `ReminderPlanTest`, `ClaudeParsingTest` | progression, scheduling, model output |

The rule tests were written before the rules and run red first — eleven of twelve
failed. The twelfth passed because deloads had been implemented two phases
earlier.

---

## What failed

This is the part worth reading.

**A figure that could not stand on the floor.** The pike push-up's hips sat 0.401
above the floor with a leg chain reaching 0.328. Its feet had been hovering for
weeks. Found only when a diagnostic printed *each* contact point rather than the
lowest — the lowest was the hands, exactly on the floor, so it looked perfect. The
fix meant restaging every joint as a circle intersection, and the honest result is
a flatter pike than the impossible one it replaced.

**Seven figures that drew the wrong rung.** `base_rung` defaulted to 0, but the
pull-up is drawn as a full bodyweight pull-up — its *second* rung, not
"negatives or feet-assisted". So a beginner saw a full pull-up with no caveat,
the app claiming a drawing was of the thing they were doing.

**A rule that explained away the data.** A recorded sleep starting at 4:50 a.m.
was judged "too late to be a bedtime" and dismissed as a nap. It was a real
bedtime. The app was hiding the largest miss in the log behind a reassuring
sentence. Length, not start hour, is the honest test.

**"365 minutes late."** Arithmetically correct and unreadable at a glance, which
is the only way it is ever read. Now "6h 5m late".

**A rung the tempo parser could not read.** `"3-second slide-out"` asked for three
seconds while the figure moved at normal speed — the picture contradicting the
words beside it.

**A 7-day average that was not one.** It took the first seven *rows*, and rows
only exist for days something was logged. Miss four days and it quietly reached
back a fortnight. The existing tests passed because they only ever used
consecutive days.

**Two commits that lied.** A `perl` substitution silently matched nothing while
the commit message said the change was made. Caught by a report that counted what
was actually missing rather than trusting the edit.

**A feature switched off by one word in a build file.** Making the Samsung SDK
optional meant moving its code to `src/samsung/java` and adding that root only
when the `.aar` is present. The build added it with `java.srcDir`. The folder
holds `.kt` files, and a Java source root containing no Java compiles nothing
without complaining — so the classes were never built, the reflective lookup
found nothing and swallowed it exactly as designed, and the app reported "not
built with the Samsung SDK" on a phone that had it. Green build, green tests,
feature gone. Found only by checking the *other* configuration: the no-SDK build
compiled no Samsung classes, which is correct, and so did the one with the SDK,
which is not. Comparing the two is what made it visible; neither on its own
would have.

**Two tests that passed for no reason.** After fixing the session-colour bug I
reverted the keyword table to check the new test caught it. It still passed.
Reverted the match ordering instead — still passed. Either fix alone covered the
case, so reverting one proved nothing; only with both reverted did it go red. A
test called "verified" after the first check would have been verified against
nothing.

The pattern: every one of these was found by something that *rendered* or
*counted* rather than asserted. A test that says "this should equal that" only
finds what you thought to check.

---

## Building it

```bash
./gradlew test testDebugUnitTest  # 215 tests, no device needed
./gradlew installDebug           # needs a phone; minSdk 28
```

Optional, and the app runs without both:

- **Claude layer** — deploy `worker/` to Cloudflare, then add
  `cutcoach.proxyUrl` and `cutcoach.proxySecret` to `local.properties`.
- **Samsung Energy Score** — see `app/libs/README.md`. The SDK is a licensed
  .aar that is not in this repository; `src/samsung/` is compiled only when it is
  present, and nothing outside that folder names Samsung, so a clone without it
  builds and tests normally. A unit test enforces both halves of that.

Your own protocol goes in `app/src/main/assets/protocol.local.json`, which is
gitignored and used in preference to the sample.

---

## Licence

The figures, the icons and the code are original work. Demo exercise videos play
through YouTube's own embedded player and are never downloaded or re-hosted. The
Atkinson Hyperlegible font ships under its own licence, included.
