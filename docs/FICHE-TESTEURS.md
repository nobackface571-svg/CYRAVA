🇬🇧 **English** | 🇫🇷 [Français](FICHE-TESTEURS.fr.md)

# CYRAVA — Tester guide

This guide sums up, step by step, how to install CYRAVA, allow it and try it. It takes a few minutes to read. For all the details, see the [README](../README.md).

> **Good to know before you start**
> - CYRAVA works on **Android 8 or later**. The "restricted settings" steps only apply to **Android 13 and later**.
> - CYRAVA **has no Internet access** and **only** reads the apps you tick.
> - Menu names may vary slightly depending on the phone brand (Samsung, Xiaomi, Pixel…).
> - The app is available in **English and French**: **Settings › Language**.

---

## Step 0 — Turn off Google Play Protect (important)

CYRAVA is not on the Play Store and uses the accessibility service: **Play Protect is very likely to block the installation**, or flag it as dangerous. Turn it off **before** installing:

1. Open the **Google Play Store** app.
2. Tap your **profile picture** (top right) › **Play Protect**.
3. Tap the **gear ⚙️** (top right).
4. Turn off **"Scan apps with Play Protect"**, then confirm.

> Once CYRAVA is installed and turned on (step 3), you can turn Play Protect back on in the same place. If it then shows a warning about CYRAVA, choose to **keep** the app.

✅ **Check**: the Play Protect page says scanning is turned off.

---

## Step 1 — Download and install the APK

1. From your phone's browser, open the **Releases** tab of this GitHub repository.
2. Download the **.apk** file of the latest version.
   > **Only** install CYRAVA from this page: an APK found elsewhere may have been modified.
3. Tap the download notification (or **My Files › Downloads**), then the `.apk` file.
4. If Android shows *"… is not allowed to install unknown apps from this source"*:
   tap **Settings**, turn on **Allow from this source**, then go back.
5. Tap **Install**.
   - **Play Protect** still shows a warning? → **More details › Install anyway**. If it blocks, go back to step 0.
   - **Recent Samsung**, installation blocked? → **Settings › Security and privacy › Auto Blocker**: turn it off during installation, then turn it back on.

✅ **Check**: the CYRAVA icon appears among your apps.

---

## Step 2 — Allow "restricted settings" (Android 13 and later)

For an app installed outside the Play Store, Android **greys out** the accessibility service until you allow it. This is an Android protection, not a bug.

1. Open CYRAVA. On **Home**, tap **Open accessibility settings**.
2. Go to **Installed apps › CYRAVA – Digital Privacy**.
3. The switch is greyed out or a **"Restricted setting"** message appears: tap **OK**.
   > ⚠️ This attempt is **required**: the step 5 menu only appears after it.
4. Go back to CYRAVA and tap **Open app info**
   (or: **Settings › Apps › CYRAVA**).
5. Tap **⋮** (three dots, top right) › **Allow restricted settings**.
6. Confirm with your PIN, pattern or fingerprint.

✅ **Check**: CYRAVA's switch in Accessibility is no longer greyed out.

> **The ⋮ menu does not show "Allow restricted settings"?**
> Repeat steps 1 to 3 (try to turn on the service), then go back to step 4.

---

## Step 3 — Turn on the accessibility service

1. **Settings › Accessibility › Installed apps › CYRAVA – Digital Privacy**
   (or the **Open accessibility settings** button in CYRAVA).
2. Turn on the switch.
3. Android shows a generic warning about screen control: accept it.
   CYRAVA only reads the ticked apps and has no Internet access.
4. Go back to CYRAVA.

✅ **Check**: Home no longer shows **"Protection off"**.

From then on, **you no longer need to open CYRAVA**: it works in the background. After a restart or an update, the service stays on.

---

## Step 4 — Stop the phone from shutting CYRAVA down

Especially useful on **Samsung** (and Xiaomi, Huawei, Oppo…), which readily stop background services.

1. In CYRAVA, **Battery** card on Home: tap **Don't optimise CYRAVA** and accept.
2. Samsung: **Settings › Battery › Background usage limits › Never sleeping apps › +** › tick **CYRAVA**.
3. Optional: **Settings › Apps › CYRAVA › Battery › Unrestricted**.

> Home says the service is switched on but stopped? Turn the accessibility service off and on again (step 3).

---

## Step 5 — Choose the apps and the profile

1. **Apps** tab: turn on the switch of the apps to monitor.
   - **Recommended browsers**: **Chrome** or **Firefox**.
   - **Samsung Internet is not supported** (it does not pass on page content).
   - **Never** tick: banking, password manager, messaging, games with anti-cheat.
2. **Settings › Rejection profile**: choose **Strict** for testing.
   > In the **Observation** profile, CYRAVA analyses but touches nothing: so there is nothing to test.

---

## Step 6 — Try it

1. In Chrome, open an **incognito** tab (a site that has already received your choice no longer shows its pop-up).
2. Open a site that shows a cookie pop-up (news, recipes, shopping site…).
3. **Do not touch the screen** for about ten seconds.
4. Open CYRAVA › **History** tab and look at the site's row.

**After a failure**, wait **10 minutes** before testing the same page again: CYRAVA does not retry the same pop-up before then.

> **Good to know**
> - A pop-up disappearing does not prove the rejection was saved. Only the "switches verified off" result in the history means boxes were re-read as off.
> - To check for yourself: reopen the page, tap "Cookies" or "Manage my preferences" at the bottom of the page, and look at the state of the boxes.
> - CYRAVA does not delete cookies already set: it rejects new ones.

---

## Step 7 — Give your feedback

Open this GitHub repository's **Issues** tab, then **New issue**, and choose:

| Situation | Form to choose |
|---|---|
| CYRAVA tapped **"Accept"**, "Allow", a purchase or a subscription | 🚨 **CYRAVA tapped an accept button** (serious bug) |
| Pop-up not recognised, partial rejection, button not found, app stuck | 🐞 **A cookie pop-up is not (properly) rejected** |

The forms are in English and French; you can write in either language.

**Attach if possible:**

- the **site or app** concerned, the date and time;
- the **phone model** and **Android version**;
- the **CYRAVA version** (name of the installed `.apk` file);
- a **screenshot** of the History row;
- the **diagnostic file**: **Settings › Diagnostic centre › Capture in 5 seconds** (pop-up shown on screen), then **Share**.
  > This file contains the text shown on screen at that moment, never the content of input fields or passwords. Read it before publishing it.

---

## Uninstall or pause

- **Pause**: **Settings › Accessibility › CYRAVA** › turn off the switch. CYRAVA stops immediately and never turns itself back on.
- **Update**: install the new APK **over** the old one; settings and history are kept.
- **Uninstall**: long-press the icon › **Uninstall**.

Thank you for testing CYRAVA!
