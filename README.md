# Sergak — raqamli hayotingiz himoyachisi

**Sergak** (o'zbekcha "hushyor") is an Android app that protects people in Uzbekistan from the attacks that are stealing their money and accounts right now: SMS-stealer APKs sent over Telegram, fake `gov.uz` "compensation" pages, "vote in a contest" code theft, fake bank-employee calls, and hacked-friend money requests.

The interface is in Uzbek (Latin). The detection engine also understands Uzbek Cyrillic and Russian.

> 🔒 **Privacy by design:** message text is never stored or sent anywhere. Two builds:
> - **offline** (`uz.sergak.offline`): **no INTERNET permission at all**, for government and air-gapped use. Everything runs on the device.
> - **online** (`uz.sergak`): adds opt-in cloud checks through *your* Sergak server. Only APK **SHA-256 hashes** and links the user chooses to check are sent. API keys live on the server, never in the APK.

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

## Real-threat detection

| Layer | Where | What it catches |
|---|---|---|
| **APK static analysis** | on device | Second APK or DEX hidden in `assets/` (droppers), high-entropy encrypted payloads (`.dat` / `.key`, the MidnightDat and RoundRift patterns), the known `libandroidcore_native.so` dropper library |
| **Fake-app detection** | on device | A sideloaded app named "Click", "Payme", "Telegram", "Google Play", "Update", or disguised as a file ("Sud qarori.pdf", "To'ydan video") |
| **IOC hashes** | on device + server feed | Known Uzbek SMS-stealer samples (Group-IB 2025, bundled), plus a daily feed from your server (`/v1/feed`) |
| **VirusTotal** (70+ AV engines) | server | Hash lookup for sideloaded apps and APK files *before install*, plus URL lookup |
| **MalwareBazaar** (abuse.ch) | server | Confirmed malware hashes (Ajina, Wonderland…) |
| **URLhaus** (abuse.ch) | server | Malware-distribution URLs |
| **Google Web Risk / Safe Browsing** | server | Phishing and malware sites |

**Check an APK before installing it:** in Telegram, use *Share* or *Open with → Sergak*, or the "APK faylni tekshirish" button. The file is copied to the app's cache, analysed and deleted.

## Backend (`server/`): deploy, then add keys

Spring Boot 3.5 / Java 21 / PostgreSQL. Every provider is **off until its key is set**, so you can deploy now and add keys later.

```bash
cd server
cp .env.example .env        # fill in keys (see below)
docker compose up -d        # API on :8080, Postgres in a volume
curl localhost:8080/v1/info # shows which providers are enabled
```

Or use the image CI publishes: `ghcr.io/dev-1-lab/sergak-api:latest` (make the package public under GitHub → Packages, or `docker login ghcr.io`).

| Env var | Where to get it | Notes |
|---|---|---|
| `VT_API_KEY` | virustotal.com → API key | **The public key is not allowed in commercial products** (4 req/min, 500/day). Get Premium for a public launch and raise `VT_RPM` / `VT_RPD` |
| `ABUSECH_AUTH_KEY` | auth.abuse.ch (free) | One key covers MalwareBazaar and URLhaus |
| `GOOGLE_API_KEY` + `GOOGLE_MODE` | Google Cloud console | `web-risk` (commercial use allowed) or `safe-browsing` (non-commercial only) |
| `SERGAK_APP_TOKEN` | `openssl rand -hex 24` | Must match the app build. It isn't a real secret (it can be pulled out of the APK); it only filters casual bots. Plan Play Integrity for production |
| `SERGAK_ADMIN_TOKEN` | `openssl rand -hex 32` | Enables `/admin/ioc` |
| `DB_PASSWORD` | – | Postgres password |

Put it behind HTTPS (nginx or Caddy). It reads `X-Forwarded-For` for rate limiting.

**Point the app at your server:** in GitHub, go to *Settings → Secrets and variables → Actions*. Set the variable `SERGAK_API_URL=https://api.your-domain.uz` and the secret `SERGAK_APP_TOKEN`, then re-run the workflow. Locally:
`./gradlew assembleOnlineRelease -PsergakApiUrl=https://… -PsergakAppToken=…`

### API

| Method | Path | Body / notes |
|---|---|---|
| `POST` | `/v1/hashes` | `{"sha256":["…"]}`, up to 50 → `{"results":[{key,status,detections,engines,label,sources,cached,partial}]}` |
| `POST` | `/v1/url` | `{"url":"…"}`. `?query` and `#fragment` are stripped before lookup (`SERGAK_STRIP_URL_QUERY`) |
| `GET` | `/v1/feed` | IOC hashes and phishing domains, with ETag |
| `GET` | `/v1/info` | Enabled providers |
| `POST` | `/admin/ioc` | `X-Admin-Token`; `{"type":"SHA256|SHA1|DOMAIN","value":"…","label":"…","source":"…"}` |

`status` is one of `MALICIOUS`, `SUSPICIOUS`, `CLEAN` or `UNKNOWN`.

How the server keeps lookups private and within quota:
- **Unknown URLs are never submitted to VirusTotal.** Submissions become public there and could leak private tokens.
- **Files are never uploaded.** Only hashes are sent.
- **Results are cached in Postgres** (malicious 7 days, clean 1 day, unknown 6 hours) to save VirusTotal quota.
- **Rate limits** apply per install ID and per IP.

## Detection engine (`app/src/main/java/uz/sergak/core`)

Pure Kotlin with no Android dependencies, fully unit-tested (`./gradlew testOnlineDebugUnitTest`).

- `Normalizer`: Cyrillic (Uzbek and Russian) → Latin, apostrophe unification, leetspeak.
- `LinkAnalyzer`: official-domain allowlist, brand impersonation (`my-gov-uz.online`, `uzcard-kompensatsiya.online`), look-alikes by edit distance (`paymee`, `c1ick`), punycode / IDN, `user@host` tricks, IP hosts, shorteners, risky TLDs, `.apk` downloads, fake Telegram bots.
- `MessageAnalyzer`: ~20 weighted rules (code request with negation handling, card data, APK and double extensions, court / wedding / prize / vote / loan lures, impersonation, urgency, money requests, remote-access apps, drop-card offers, passport requests). It also recognises genuine OTP messages so they aren't flagged.
- `AppRiskScorer`: an SMS-stealer profile based on Group-IB's analysis of Ajina, Qwizzserial and Wonderland.

## Build

Requirements: JDK 17 and the Android SDK (API 35).

```bash
./gradlew testOnlineDebugUnitTest assembleOnlineDebug assembleOfflineDebug
# APKs: app/build/outputs/apk/{online,offline}/debug/
```

Every push to `main` builds both APKs and the server image in GitHub Actions, and publishes the APKs to the **`latest`** release so you can download them straight to a phone.

## Sources used for the threat model

- Group-IB, *Choose Your Fighter: A New Stage in the Evolution of Android SMS Stealers in Uzbekistan* (2025)
- IIV Kiberxavfsizlik markazi warnings: kun.uz, spot.uz, zamin.uz, anhor.uz (2025–2026)
- UZB Cyber Kalkan (Google Play) and the IIV Cyber Shield bot (@cybershielduz_bot), reviewed as existing local solutions
