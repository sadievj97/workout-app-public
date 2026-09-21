# CutCoach: prompts to paste into Claude Code, in order

Open Claude Code inside the project folder, which holds CLAUDE.md, protocol.json, exercises.json, and this file. Paste one prompt at a time. Do not move to the next prompt until the current step works on your phone.

## Prompt 0: review and decisions (no code yet)

```
Read CLAUDE.md, protocol.json, and exercises.json in this folder. Do not write any code yet.
1. Explain in plain language what we are building and the six phases.
2. Go through every item in open_decisions and proposed_rules_needing_approval in protocol.json. Ask me one at a time, give your recommended default, and wait for my answer.
3. Write my answers back into protocol.json.
4. Then propose a short plan for Phase 1: screens, data model, and how I will run the app on my phone. Wait for my approval.
```

## Prompt 1: build Phase 1 (offline core)

```
Implement Phase 1 from CLAUDE.md. Create the Android project with Kotlin, Jetpack Compose, and Room. Load protocol.json and exercises.json from assets as the single source of truth. Work in small steps and commit to git after each working step. When Phase 1 is done, tell me click by click how to open the project in Android Studio, connect my Samsung phone by USB, and run the app. Then explain the three most important files in plain language.
```

## Prompt 1b: how-to animations (Phase 1b)

Before pasting: for each exercise, pick one demo video you trust and note the seconds where the clean demonstration starts and ends. You will enter the link and the times in Settings.

```
Implement Phase 1b from CLAUDE.md. Build the animation engine and only three exercises first (push-up, pull-up, hollow hold), then stop and tell me how to see them on the phone. After I approve the look, animate the remaining twelve, build the Form check screen, and add the looping video segments to the cue cards.
```

## When something breaks (use any time)

```
Here is what happened: [paste the full error text from Android Studio, or describe what you saw on the phone]. Explain the cause in one paragraph, fix it, and tell me exactly what to click next.
```

Tip: when Android Studio shows a red Gradle error, copy the entire message, not just the first line.

## Prompt 2: watch data (Phase 2)

```
Implement Phase 2 from CLAUDE.md, read only. Verify every Health Connect permission name against the official documentation linked in CLAUDE.md before declaring it. Include the privacy-policy rationale activity Health Connect requires, and request the history and background read permissions. Build the Data Check screen first and stop, so I can tell you which data types my phone actually provides before you build anything that depends on them.
```

## Prompt 3: rules and recommendations (Phase 3)

```
Implement Phase 3 from CLAUDE.md. Write the twelve rule tests listed in CLAUDE.md first, show me that they fail, then implement the rules until they pass, and show me the results. Every rule reads its numbers from protocol.json. Show each recommendation on the Today screen with a one-line reason.
```

## Prompt 4: Claude layer (Phase 4)

```
Implement Phase 4 from CLAUDE.md. Start with the Cloudflare Worker proxy that stores my Anthropic API key as a secret, and walk me through creating the Cloudflare account and setting the secret step by step. The key must never be in the app, in git, or in logs. Then add the substitute, harder-variation, and weekly-review features. Every Claude response must be JSON that the rules engine validates against the guardrails; show me an example of a rejected suggestion.
```

## Prompt 5 (optional): Energy Score (Phase 5)

```
Implement Phase 5 from CLAUDE.md. Read the Samsung Health Data SDK documentation linked in CLAUDE.md first, then tell me the exact manual steps to download the SDK and enable developer mode on my phone before you write any code.
```

## Prompt 6: portfolio (Phase 6)

```
Prepare this project for a public GitHub repository as described in Phase 6 of CLAUDE.md. Replace all personal data with sample data, then check the entire git history for secrets and personal health data and report what you found before anything is published.
```

## Daily-use tips

- On cardio days (Tuesday, Thursday, Saturday), start the matching workout on the watch so its heart rate record lands in Samsung Health. The app uses it for zone feedback.
- Samsung's battery optimization can put apps to sleep. If the daily sync stops, exempt CutCoach from battery optimization in the phone's settings.
- Log honestly, including skipped sessions. The recommendations are only as good as the log.
