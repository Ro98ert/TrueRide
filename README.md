# TrueRide

[![CI](https://github.com/Ro98ert/TrueRide/actions/workflows/ci.yml/badge.svg)](https://github.com/Ro98ert/TrueRide/actions/workflows/ci.yml)

TrueRide is an Android assistant for rideshare drivers in Romania. It reads the ride offer that is already on the screen and shows a card that says whether the offer is worth taking. The card shows numbers such as price per km, price per hour and estimated profit. The driver decides.

TrueRide is read-only: it never accepts or declines rides.

Not affiliated with Uber or Bolt. Uber and Bolt are trademarks of their respective owners.

## What this repository contains

Only the pure Kotlin/JVM core of the project. The Android app is not included. The four modules below have no Android dependency, so their tests run on a laptop in seconds.

| Module | What it does |
|---|---|
| `core-model` | Data types: parsed offer, offer input, cost, thresholds, evaluation, verdict, units. Depends on the Kotlin standard library only. |
| `core-calc` | Money math with `BigDecimal`: income per hour, per km and per minute, estimated cost and profit, and the verdict against the driver's thresholds. |
| `core-parse` | Turns OCR lines from a ride-offer screen into a parsed offer. It applies rule sets (Uber RO, Uber EN, Bolt RO) and extracts numbers and units only. |
| `core-lant` | The card state machine: which source wins (accessibility nodes or OCR), the card states, and the rule that no number from a previous offer is shown. |

Dependencies: `core-calc` and `core-parse` depend on `core-model`. `core-lant` depends on `core-parse` and `core-calc`.

## Rules the code follows

- No Android on the classpath. The modules are plain Kotlin/JVM, so `import android.*` does not compile.
- Money is `BigDecimal`, never floating point.
- Rounding happens once, at display time. Values in the calculation chain are not rounded.
- Division by zero is a state, not an exception. A zero or missing total time makes the indicator unavailable.
- Missing data is shown as unknown. A field that cannot be read stays `null`, never 0.
- The parser extracts numbers and units only. It does not extract addresses or names.
- A rule set is separate from the parser engine. A layout change in a ride app touches a rule file, not the engine.

## Fixtures

The `fixtures/` directory holds anonymized captures of ride-offer screens. OCR lines are masked. Most captures have a hand-written expected result (`*.asteptat.json`) that the tests compare against. The `uber-en` captures have none, and no published test reads them. Some fixture sets are not published.

## Build and test

The Gradle daemon needs JDK 25 (see `gradle/gradle-daemon-jvm.properties`). The compiled bytecode targets Java 17.

```
./gradlew :core-model:test :core-calc:test :core-parse:test :core-lant:test
```

The `test` tasks are the supported entry point. The `replay` and `explorare` tasks in `core-parse` are developer tools driven by private scripts and are not documented here.

The last full run had 653 tests, 11 of them skipped by design (`CazuriGresiteDezactivateTest`).

## Language

Identifiers and comments are in Romanian. Comments cite decision IDs such as `D-033`. They refer to a private decision log that is not published.

## Built with AI assistance

TrueRide was built with Claude Code. See [AI_WORKFLOW.md](AI_WORKFLOW.md) for the method.

## License

All rights reserved. The source is published for review. There is no permission to use, copy, modify or distribute it beyond what the GitHub Terms of Service grant (viewing and forking on GitHub). See [LICENSE](LICENSE).
