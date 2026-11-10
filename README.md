# TrueRide

[![CI](https://github.com/Ro98ert/TrueRide/actions/workflows/ci.yml/badge.svg)](https://github.com/Ro98ert/TrueRide/actions/workflows/ci.yml)

**An Android assistant for ride-hailing drivers in Romania: before you accept a ride, it shows what the ride really pays.**

![TrueRide: lei/km and lei/hour for every offer](docs/images/hero.png)

## The problem

A driver has a few seconds to accept or decline a ride. The price is on screen, but what the ride really earns per kilometre and per hour, after the empty drive to the pickup and the cost of the car, is not. The decision is made on instinct, and bad rides get accepted often.

## How it works

1. **Reads.** When an offer appears, TrueRide reads the numbers on screen: price, kilometres, minutes. Numbers only, no names or addresses.
2. **Calculates.** Lei per kilometre, lei per hour and the estimated profit after your car's cost.
3. **Shows.** A card over the offer, in the colour of the verdict: Excellent, Good or Poor. You decide.

The app and the site are in Romanian, so the screenshots below are too (Excelent = Excellent, Bun = Good, Slab = Poor).

![Verdict examples: Excellent, Good, Poor](docs/images/verdicte.png)

### In the app

The verdict card in the app's demo mode (made-up offer, day and night themes):

| Day | Night |
|---|---|
| ![TrueRide card in the app, day theme](docs/images/app-card-zi.png) | ![TrueRide card in the app, night theme](docs/images/app-card-noapte.png) |

### On the website

| Website calculator | On a phone |
|---|---|
| ![Calculator filled in with a sample offer](docs/images/calculator.png) | ![TrueRide page on mobile](docs/images/mobil.png) |

*All numbers in the images are sample offers, not real rides.*

## Promises

- **Read-only.** TrueRide never accepts or declines a ride for you.
- **The screen image never leaves the phone.** Only numbers and units are used; names and addresses are not kept.
- **Public formulas.** The money math is in this repository, with tests.

## Status

- The Android app runs and is being tested with drivers. **Google Play: coming soon.**
- The website is in progress and will be published when it is finished.
- The calculation and parsing core (this repository) has 653 automated tests, run on every change.

## About me

I'm [Robert Amartisoaei](https://github.com/Ro98ert), and I built TrueRide with the help of AI ([the method](AI_WORKFLOW.md)).

Not affiliated with Uber or Bolt. Uber and Bolt are trademarks of their respective owners.

---

## For developers

TrueRide is read-only: it never accepts or declines rides.

### What this repository contains

Only the pure Kotlin/JVM core of the project. The Android app is not included. The four modules below have no Android dependency, so their tests run on a laptop in seconds.

| Module | What it does |
|---|---|
| `core-model` | Data types: parsed offer, offer input, cost, thresholds, evaluation, verdict, units. Depends on the Kotlin standard library only. |
| `core-calc` | Money math with `BigDecimal`: income per hour, per km and per minute, estimated cost and profit, and the verdict against the driver's thresholds. |
| `core-parse` | Turns OCR lines from a ride-offer screen into a parsed offer. It applies rule sets (Uber RO, Uber EN, Bolt RO) and extracts numbers and units only. |
| `core-lant` | The card state machine: which source wins (accessibility nodes or OCR), the card states, and the rule that no number from a previous offer is shown. |

Dependencies: `core-calc` and `core-parse` depend on `core-model`. `core-lant` depends on `core-parse` and `core-calc`.

### Rules the code follows

- No Android on the classpath. The modules are plain Kotlin/JVM, so `import android.*` does not compile.
- Money is `BigDecimal`, never floating point.
- Rounding happens once, at display time. Values in the calculation chain are not rounded.
- Division by zero is a state, not an exception. A zero or missing total time makes the indicator unavailable.
- Missing data is shown as unknown. A field that cannot be read stays `null`, never 0.
- The parser extracts numbers and units only. It does not extract addresses or names.
- A rule set is separate from the parser engine. A layout change in a ride app touches a rule file, not the engine.

### Fixtures

The `fixtures/` directory holds anonymized captures of ride-offer screens. OCR lines are masked. Most captures have a hand-written expected result (`*.asteptat.json`) that the tests compare against. The `uber-en` captures have none, and no published test reads them. Some fixture sets are not published.

### Build and test

The Gradle daemon needs JDK 25 (see `gradle/gradle-daemon-jvm.properties`). The compiled bytecode targets Java 17.

```
./gradlew :core-model:test :core-calc:test :core-parse:test :core-lant:test
```

The `test` tasks are the supported entry point. The `replay` and `explorare` tasks in `core-parse` are developer tools driven by private scripts and are not documented here.

The last full run had 653 tests, 11 of them skipped by design (`CazuriGresiteDezactivateTest`).

### Language

Identifiers and comments are in Romanian. Comments cite decision IDs such as `D-033`. They refer to a private decision log that is not published.

### License

All rights reserved. The source is published for review. There is no permission to use, copy, modify or distribute it beyond what the GitHub Terms of Service grant (viewing and forking on GitHub). See [LICENSE](LICENSE).
