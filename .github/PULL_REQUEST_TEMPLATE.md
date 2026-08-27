## What this changes

<!-- A short description of the change and why it is needed. -->

## How it was verified

<!--
Be specific and be honest. "Verified by trace, not run" is a useful answer; silence is not.
-->

- [ ] `.\gradlew.bat build` passes, unit tests included
- [ ] `.\gradlew.bat runClient` starts cleanly (if client code changed)
- [ ] Relevant items from `docs/MANUAL_TEST_CHECKLIST.md` re-run (if behavior changed)

What I verified in a running client:

What I did **not** verify:

## Design rule check

MineBro's core rule is that the model reasons, and deterministic code decides what is true.

- [ ] No model output is treated as game state (inventory counts, recipes, success/failure)
- [ ] No action is reported successful before the game state change is verified
- [ ] The client thread is never blocked; no `.get()` or `.join()` on a provider or
      conversation future in render, input, or command callbacks
- [ ] Any new tool is registered with an explicit permission level

## Notes for reviewers

<!-- Anything you are unsure about, or deliberately left out of scope. -->
