# app/libs

## samsung-health-data-api-1.1.0.aar

Not in this repository. It is Samsung's, downloaded by hand, and not ours to
redistribute.

Phase 5 reads Samsung Health's Energy Score, which Health Connect does not
expose. Everything else in the app works without it.

### To build the Samsung parts

1. Sign in at <https://developer.samsung.com/health/data/overview.html>
2. Download **Samsung Health Data SDK v1.1.0**
3. Unzip it and put `samsung-health-data-api-1.1.0.aar` in this folder
4. On the phone: Samsung Health → ⋮ → Settings → About Samsung Health → tap the
   version line ten times → **Developer mode (Samsung Health Data SDK)** →
   turn on **Developer Mode for Data Read**

### Without the file

`app/src/main/java/com/cutcoach/samsung/` imports SDK classes directly, so it
will not compile. Delete that folder **and** the `SamsungEnergyScores.register`
line in `MainActivity.onCreate`, or download the SDK. The build does not degrade
gracefully and this file exists so that is not a surprise.

Nothing else references the SDK. The readiness rule reaches Energy Score through
`health/EnergyScoreSource.kt`, an interface that does not know Samsung exists,
so the rules engine keeps compiling either way.

Samsung's developer mode is documented as being for testing and debugging only.
An app that uses the SDK without a Samsung partnership works **only** while
developer mode is on, which is why nothing in the protocol depends on Energy
Score.
