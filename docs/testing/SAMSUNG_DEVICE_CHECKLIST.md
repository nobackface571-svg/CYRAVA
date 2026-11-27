🇬🇧 **English** | 🇫🇷 [Français](SAMSUNG_DEVICE_CHECKLIST.fr.md)

# Validation checklist on a real Samsung phone

This checklist is to be filled in with the user's physical phone. Emulator results do not
replace these tests. Do not turn on automated tests on apps that were not chosen.

## Preparation

- [ ] Note the Samsung model, Android/One UI version, CYRAVA version and Chrome version.
- [ ] Only install the test APK whose commit and SHA-256 are known.
- [ ] Check the permissions and turn on the accessibility service deliberately.
- [ ] Explicitly select a test app/site.
- [ ] Turn on the Observation profile for the first pass.
- [ ] Prepare a known test consent page and note its URL.
- [ ] For physical UMP, get the `TEST-DEVICE-HASHED-ID` from the logs and provide it via
  `UMP_DEMO_TEST_DEVICE_HASHED_ID`, never in the repository.

## Safety checks

- [ ] An "Accept all", "Accept" or equivalent button is never triggered.
- [ ] A label containing a negation or an explanatory sentence is not taken for a Reject button.
- [ ] A control whose state is unknown is never acted on.
- [ ] An ambiguous "Off" state is not considered proof of OFF.
- [ ] After an action, the state is re-read; Android accepting the action alone is not counted as a success.
- [ ] A pop-up that disappears after the tap is not counted as a verified rejection.
- [ ] Input fields and sensitive subtrees are neither read nor exported.

## Navigation and pop-ups

- [ ] Banner in the foreground in Chrome.
- [ ] Going from the banner to the preferences.
- [ ] Collapsed then expanded sections.
- [ ] Long vendor list with scrolling.
- [ ] Back to top/bottom and scroll events.
- [ ] System window, dialog or keyboard on top: no action outside the target pop-up.
- [ ] Rotation or window resizing if the model allows it.
- [ ] Switching from one app to another: no unmonitored package is inspected.

## Real UMP

- [ ] The GDPR message was published in AdMob for the app ID used.
- [ ] The UMP form really appears on the phone.
- [ ] The diagnostic identifies the controls exposed by Android.
- [ ] Each preference state stays unknown if Android does not publish a reliable value.
- [ ] Actions, if allowed, are re-read and verified one by one.
- [ ] The privacy options form is available if the SDK declares it required.
- [ ] The final result does not rely only on `consentStatus`, `canRequestAds` or the pop-up closing.

## History, diagnostics and stability

- [ ] History: distinguish verified, partial, unverifiable, impossible, interrupted and observed.
- [ ] The numbers of attempted/accepted/blocked actions match the logs.
- [ ] CSV and JSON match the visible history.
- [ ] Diagnostics say whether the capture is truncated.
- [ ] No typed or sensitive data in exported diagnostics.
- [ ] History kept after the process is closed/restarted.
- [ ] Emergency stop: no further action after it is triggered.
- [ ] Service stopped by Android: the user is clearly warned.
- [ ] Check battery use/heat and that there is no event loop after scrolling.

## Session result

- Date/time:
- Model / Android / One UI:
- Chrome:
- APK version / commit:
- App and URL tested:
- AdMob UMP form used:
- Screenshots / diagnostics:
- Verified successes:
- Partial / unknown results:
- Reproducible failures:
- No consent accepted: yes/no
- Notes:
