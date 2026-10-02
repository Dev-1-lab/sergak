# Sergak — raqamli hayotingiz himoyachisi

**Sergak** (o'zbekcha "hushyor") is an Android app that protects people in Uzbekistan from the attacks that are stealing their money and accounts right now: SMS-stealer APKs sent over Telegram, fake `gov.uz` "compensation" pages, "vote in a contest" code theft, fake bank-employee calls, and hacked-friend money requests.

The interface is in Uzbek (Latin). The detection engine also understands Uzbek Cyrillic and Russian.

> 🔒 **Privacy by design:** the app has **no INTERNET permission at all** (it's stripped in the manifest). Every check runs on the device, and message text is never stored or sent anywhere.

## Features

| Section | What it does |
|---|---|
| **Asosiy** (Home) | Protection score, next recommended step, quick actions, SOS button |
| **Tekshirish** (Check) | Paste or share any message, link or file name and get a verdict with plain-language reasons. Works from Telegram via *Share → Sergak* or *select text → "Sergak'da tekshirish"* |
| **Real-time guard** | A notification listener that checks incoming Telegram / SMS / WhatsApp / imo / Viber messages on the device. It warns about `.apk` lures, phishing links and code/card requests, and reminds you never to share an OTP when a Telegram login code arrives |
| **Skaner** (Scan) | Finds apps with SMS-stealer traits: sideloaded (and *which app* installed them, e.g. Telegram), SMS permissions, hidden launcher icon, Accessibility, notification access, device admin, can install other apps. Also checks screen lock, whether Telegram can install APKs, patch age, dev mode / ADB and root |
| **New-install alerts** | Instantly (or every 15 min as a fallback) flags newly installed sideloaded apps, with a one-tap uninstall button |
| **Himoya** (Protect) | A step-by-step hardening plan for the phone, Telegram (2FA, sessions, privacy, auto-download, cards in Saved Messages, @cybershielduz_bot) and payment apps (Click / Payme / bank apps). Steps are checked automatically where possible |
| **O'rganish** (Learn) | 9 lessons on real local schemes plus an 8-question scenario quiz |
| **SOS** | An "I've been scammed or hacked" playbook with call buttons for 102, the IIV hotline, the Cybersecurity Center (24/7) and the Central Bank hotline |

## Detection engine (`app/src/main/java/uz/sergak/core`)

Pure Kotlin with no Android dependencies, fully unit-tested (`./gradlew testDebugUnitTest`).

- `Normalizer`: Cyrillic (Uzbek and Russian) → Latin, apostrophe unification, leetspeak.
- `LinkAnalyzer`: official-domain allowlist, brand impersonation (`my-gov-uz.online`, `uzcard-kompensatsiya.online`), look-alikes by edit distance (`paymee`, `c1ick`), punycode / IDN, `user@host` tricks, IP hosts, shorteners, risky TLDs, `.apk` downloads, fake Telegram bots.
- `MessageAnalyzer`: ~20 weighted rules (code request with negation handling, card data, APK and double extensions, court / wedding / prize / vote / loan lures, impersonation, urgency, money requests, remote-access apps, drop-card offers, passport requests). It also recognises genuine OTP messages so they aren't flagged.
- `AppRiskScorer`: an SMS-stealer profile based on Group-IB's analysis of Ajina, Qwizzserial and Wonderland.

## Build

Requirements: JDK 17 and the Android SDK (API 35).

```bash
./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Every push to `main` builds the APK in GitHub Actions and publishes it to the **`latest`** release, so you can download it straight to a phone.

## Sources used for the threat model

- Group-IB, *Choose Your Fighter: A New Stage in the Evolution of Android SMS Stealers in Uzbekistan* (2025)
- IIV Kiberxavfsizlik markazi warnings: kun.uz, spot.uz, zamin.uz, anhor.uz (2025–2026)
- UZB Cyber Kalkan (Google Play) and the IIV Cyber Shield bot (@cybershielduz_bot), reviewed as existing local solutions
