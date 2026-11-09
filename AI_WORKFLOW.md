# How TrueRide was built with AI

TrueRide was built with Claude Code (Anthropic) by Robert Amartisoaei. Robert is new to programming. His background is hardware testing in telecom. He wrote the product rules and the checks. Claude Code wrote most of the code under those rules.

This file describes the method. It is a record of how the work is organized, not a claim about code quality. The tests in this repository are the evidence.

## Written spec as the source of truth

- A written specification describes the product: the problem, what the app does, what it must never do, the data model and the screens.
- Every session starts by reading the spec and the project instructions.
- If code and spec disagree, the spec is checked first. Then one of them is fixed on purpose.

## Decision log

- Each non-obvious choice gets an ID (D-NNN) and a short entry: what was decided and why.
- Code comments cite these IDs. That is why the Romanian comments in this repository mention `D-033` and similar.
- The log itself is private and is not part of this repository.

## Product invariants

These cannot be negotiated. If a task seems to need one of them broken, work stops and the owner is asked.

1. The app is read-only. It never accepts or declines rides and never presses buttons in another app.
2. Release builds write no screenshot and no raw OCR text to logs, crash reports or telemetry. The one documented exception is a private test build, which sends offer OCR text to the crash reporter so real parser failures can become fixtures.
3. `FLAG_SECURE` is never bypassed. A black frame is a valid state with a message to the user.
4. The parser extracts numbers and units only. No addresses, no names.
5. No number on the card comes from a previous offer. If a new offer cannot be parsed, the card disappears.
6. Missing data is shown as unknown, never as zero.

## Architecture invariants

- The core modules are plain Kotlin/JVM with no Android on the classpath. The build enforces this.
- The parser is separate from the calculation engine. Rules change often, arithmetic does not.
- Money uses `BigDecimal`. Rounding happens once, at display.
- Division by zero is a state, not an exception.

## Plan before execution

- For any task with more than two steps, Claude Code writes a short plan first: step, then how that step is verified.
- The plan has at most 10 steps. Work starts after the owner confirms it.
- After each step, one line reports what was finished and which files changed.
- A vague task is turned into a checkable goal. "Fix the bug" becomes "write a test that reproduces it, then make it pass".

## Test first, with fixtures

- Parser behavior is pinned by fixtures: anonymized captures of ride-offer screens with masked OCR lines.
- Most fixtures have an expected result written by hand, not generated from the parser output.
- A new fixture reruns the parser tests automatically, because the fixture directory is a declared test input.
- A bug report from real use becomes a fixture first, then a fix.
- Some cases the parser should get wrong are kept as disabled tests, so the gap stays visible.

## Code maps for navigation

- The private repository keeps short maps of the code (not published here): which module owns which responsibility and how data flows between them.
- Claude Code reads the map before searching, then searches only the paths the map names.
- When a change moves a responsibility, the map is updated at the end of the phase.
- New code goes into the layer the map assigns. No parallel paths.

## Review gates before commits

- Nothing is committed straight after writing. A separate review pass reads the diff first.
- The reviewer is a separate agent session that did not write the code. It checks the diff against the invariants and the plan.
- The owner is shown what will be committed before each commit.
- Pushing to a remote needs an explicit instruction from the owner each time.

## What the AI does not decide

- Product limits and invariants.
- Anything that touches data collection or store policy.
- Whether to publish.

## Why this structure

A person who is new to code cannot judge every line. Written rules, small steps, tests with hand-written expected values and independent review give checks that do not depend on that judgment.
