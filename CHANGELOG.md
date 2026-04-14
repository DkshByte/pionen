# Changelog

All notable changes to Pionen will be documented here.  
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),  
versioning based on [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Planned
- Real Tor daemon integration via Guardian Project `tor-android`
- Pattern unlock screen implementation
- Room database migration system (replacing `fallbackToDestructiveMigration`)
- Decoy vault PIN support
- Intruder capture review screen

---

## [1.0.0] — 2026-04-14

### Added
- **AES-256-GCM encrypted vault** — per-file keys, hardware-backed via Android Keystore / StrongBox TEE
- **Two-factor authentication** — biometric + 6-digit PIN (PBKDF2-SHA256, 100k iterations)
- **SQLCipher encrypted database** — all metadata encrypted at rest; key protected by `EncryptedSharedPreferences`
- **Secure camera capture** — photos/videos written directly to vault (no plain copy)
- **HTTPS-only secure downloader** — content ingested directly into vault
- **Panic / emergency wipe** — shake-to-wipe and PIN-triggered crypto-shredding
- **Stealth mode** — disguise app as Calculator, Notes, or System Utilities
- **Decoy vault** — decoy content for plausible deniability
- **Intruder capture** — photograph failed unlock attempts
- **Private browser** — Tor-routed WebView (daemon placeholder; routing complete)
- **Local HTTPS web server** — serve vault files over LAN with QR code pairing
- **VPN status detection** — warn when VPN is not active
- **Auto-lock on background** — vault locks instantly when app leaves foreground
- **Screenshot shield** — `FLAG_SECURE` applied globally
- **Share receiver** — import files from any Android share sheet directly to vault
- **ExoPlayer media playback** — stream video/audio from encrypted vault without disk copies
- **GitHub Actions CI** — lint, unit tests, debug APK, tag-triggered release build
- **EncryptedSharedPreferences** for database key storage (replacing plain SharedPreferences)

### Security
- All logging gated behind `BuildConfig.ENABLE_LOGGING` — zero logcat output in release
- ProGuard / R8 enabled with targeted rules (no broad Compose keep)
- All backup and cloud extraction rules blocked (`allowBackup=false`)
- Network cleartext fully disabled except localhost (for embedded HTTPS server)
- Per-file crypto-shredding: key destruction = irrecoverable file deletion
