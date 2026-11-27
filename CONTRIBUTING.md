🇬🇧 **English** | 🇫🇷 [Français](CONTRIBUTING.fr.md)

# Contributing to CYRAVA

Please read [SECURITY.md](SECURITY.md) first: safety rules come before success rate. A change that could, even rarely, lead to tapping an accept button is not acceptable.

## Golden rules

1. **Never accept.** Every new tappable phrase must pass the safety guard. When in doubt, do not tap.
2. **No network.** No `INTERNET` permission, no library that would need it, no usage statistics.
3. **Honest results.** Only an observation proves a rejection (`OPTIONS_SUCCESS`: switches re-read as off). A tap followed by the pop-up closing gives `UNVERIFIED`; any doubt gives `PARTIAL` or `IMPOSSIBLE`.
4. **No screen text** outside diagnostic files (neither in the history nor in system logs).
5. **Every decision is tested.** The logic lives in `core/` and comes with its tests.

## Adding a phrase or a platform

1. Edit `app/src/main/assets/rules.json`, in the right list: `hints` (recognition clues), `buttons.refuse`, `buttons.options`, `buttons.globalReject`, `buttons.confirm`, `buttons.vendorTabs`, `buttons.back`, `buttons.accept` (never tapped: only used to recognise the pop-up), or `platforms`.
2. Texts are compared without accents or punctuation, in lower case: write them normally.
3. A rejection phrase that contains an accept word ("Continue without accepting") becomes an exception to the safety guard: it must be an explicit negation, and a test must cover it.
4. Increase `version` in `rules.json`.
5. Run `./gradlew test`: the tests check that every acceptance is blocked, that every rejection is allowed, and that the shipped rules remain importable.

If you start from a diagnostic file, preferably add a scenario to `CmpScenariosTest` that reproduces the structure of the pop-up.

## Changing the engine

- Write the test first (in `core/src/test`), with a hand-built tree.
- Test names containing local classes must stay in plain characters (no « » or accents).
- Check the lab scenarios (`LabAndValidatorTest`): they must never accept.

## Changing the interface

- Texts are simple and jargon-free, in French (`res/values`) and English (`res/values-en`).
- If a visible text changes, update `.github/scripts/e2e.sh`, which finds elements by their text.
- No figures or statuses that do not come from real data.

## Check before sending

```sh
./gradlew test :app:lintRelease :app:assembleRelease
```

Building the app requires the Android SDK (downloaded from `dl.google.com`). Without it, `./gradlew :core:test` is still possible. Full builds and tests are run on CodeMagic, started by hand by the maintainer.

## Commit messages

In French, in the imperative or as a short title with a prefix: `core :`, `app :`, `ci :`, `docs :`, `test(e2e) :`. One sentence of context in the body if the "why" is not obvious.

## Publishing a version

Releases are published by the repository owner, with a personal signing key: see the [README, section 11](README.md#11-apk-signing-and-development). Update `CHANGELOG.md` beforehand. `bash scripts/verifier-workflows.sh` and `bash scripts/verifier-signature.sh` must stay green.
