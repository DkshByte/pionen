# Pionen 🔐

> **A privacy-first encrypted mobile vault for Android.**  
> Store files, browse privately, and protect sensitive data — all behind military-grade AES-256-GCM encryption and hardware-backed keys.

---

## Features

| Category | Capability |
|---|---|
| 🔒 **Encryption** | AES-256-GCM per-file keys, hardware-backed via Android Keystore / StrongBox TEE |
| 🗄️ **Vault** | Encrypted local vault for photos, videos, audio, documents |
| 🔑 **Authentication** | Biometric + 6-digit PIN two-factor unlock; PBKDF2-SHA256 (100k iterations) |
| 🌐 **Private Browser** | Tor-routed browser with no history, no cache |
| 📥 **Secure Downloader** | HTTPS-only content ingestion directly into the vault |
| 📷 **Secure Camera** | Capture directly to encrypted storage (no plain copies) |
| 💣 **Panic Wipe** | Shake-to-wipe / PIN-triggered crypto-shredding; all keys destroyed instantly |
| 🎭 **Stealth Mode** | Disguise app icon as Calculator, Notes, or System Utilities |
| 🛡️ **Intruder Detection** | Photograph failed unlock attempts |
| 📡 **Local Web Server** | HTTPS server to access vault files from browser on same Wi-Fi |

---

## Security Architecture

```
User Authentication (Biometric + PIN)
         │
         ▼
   Android Keystore (TEE / StrongBox)
         │  per-file AES-256 keys, non-extractable
         ▼
   FileEncryptor (AES-256-GCM)
         │  streaming encryption, random IV per file
         ▼
   SQLCipher Database (AES-256)
         │  encrypted metadata only
         ▼
   Encrypted Vault Files (*.enc on disk)
```

- **No plaintext ever touches disk.** Files are encrypted before writing.
- **No cloud sync.** `allowBackup=false`, all cloud extraction rules blocked.
- **No screenshots.** `FLAG_SECURE` applied globally.
- **No disk image cache.** Coil configured with memory-only cache.
- **Auto-lock on background.** Vault locks instantly when app leaves foreground.

---

## Requirements

| Requirement | Minimum |
|---|---|
| Android SDK | API 26 (Android 8.0 Oreo) |
| Build Tools | Android Studio Hedgehog or newer |
| JDK | 17 |
| Kotlin | 1.9.22 |

---

## Getting Started

### Clone
```bash
git clone https://github.com/DkshByte/pionen.git
cd pionen
```

### Build (Debug)
```bash
./gradlew assembleDebug
```

### Build (Release)
Before building a release APK you **must** configure signing. See [Signing Setup](#signing-setup) below.

```bash
./gradlew assembleRelease
```

---

## Signing Setup

> ⚠️ **Never commit your keystore or passwords to version control.**

1. Generate a release keystore:
   ```bash
   keytool -genkeypair -v \
     -keystore pionen-release.jks \
     -keyalg RSA \
     -keysize 2048 \
     -validity 10000 \
     -alias pionen
   ```

2. Create `keystore.properties` in the project root (it is `.gitignore`d):
   ```properties
   storeFile=../pionen-release.jks
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=pionen
   keyPassword=YOUR_KEY_PASSWORD
   ```

3. The `signingConfigs` block in `app/build.gradle.kts` reads from `keystore.properties` automatically — no extra changes needed.

For CI/CD, use GitHub Actions secrets instead of a local file. See `.github/workflows/ci.yml`.

---

## Tor Integration

Tor support is currently a **placeholder**. The `TorManager` API is complete but the daemon is simulated. To enable real Tor:

1. Add the Guardian Project Maven repository to `settings.gradle.kts` (already present).
2. Uncomment in `app/build.gradle.kts`:
   ```kotlin
   implementation("info.guardianproject:tor-android:0.4.8.12")
   implementation("info.guardianproject:jtorctl:0.4.5.7")
   ```
3. Implement `startTorDaemon()` in `TorManager.kt` using the `OrbotHelper` or the native binary wrapper.

---

## Project Structure

```
app/src/main/kotlin/com/pionen/app/
├── PionenApplication.kt        # App entry point, Hilt, Coil config
├── core/
│   ├── crypto/
│   │   ├── FileEncryptor.kt    # AES-256-GCM streaming encryption
│   │   ├── KeyManager.kt       # Android Keystore key lifecycle
│   │   └── SecureBuffer.kt     # Zeroable in-memory buffer
│   ├── network/
│   │   ├── TorManager.kt       # Tor lifecycle (placeholder)
│   │   ├── TorService.kt       # Foreground service for Tor
│   │   ├── VpnStatusManager.kt # VPN detection
│   │   └── ProxyAwareHttpClient.kt
│   ├── security/
│   │   ├── LockManager.kt      # Auth, PIN, biometric, auto-lock
│   │   ├── PanicManager.kt     # Emergency wipe
│   │   ├── ShakeDetector.kt    # Shake-to-wipe trigger
│   │   ├── ScreenshotShield.kt # FLAG_SECURE
│   │   ├── StealthManager.kt   # Icon alias switching
│   │   ├── DecoyVaultManager.kt# Decoy vault
│   │   └── IntruderCaptureManager.kt
│   └── vault/
│       ├── VaultFile.kt        # Room entity
│       ├── VaultEngine.kt      # Vault CRUD + panic wipe
│       └── VaultDatabase.kt    # SQLCipher Room DB
├── di/
│   └── AppModule.kt            # Hilt DI providers
├── ingestion/
│   ├── SecureDownloader.kt     # HTTPS → vault pipeline
│   ├── SecureCameraController.kt
│   └── ShareReceiverActivity.kt
├── media/                      # ExoPlayer integration
├── server/
│   ├── SecureWebServer.kt      # NanoHTTPD HTTPS server
│   └── WebServerService.kt
└── ui/
    ├── MainActivity.kt
    ├── navigation/
    ├── screens/                # All Compose screens
    ├── viewmodels/
    ├── components/
    └── theme/
```

---

## Known Limitations & Roadmap

| Item | Status |
|---|---|
| Tor daemon integration | 🟡 Placeholder — API ready |
| Room database migrations | 🟡 `fallbackToDestructiveMigration` used — add migrations before v2 |
| Release signing | 🟡 Must configure before first Play Store submission |
| Pattern unlock | 🟡 UI placeholder — not yet implemented |
| `security-crypto` alpha dep | 🟡 Update when stable is released |

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

To report a vulnerability, see [SECURITY.md](SECURITY.md).

## Export Control

This software includes cryptographic functionality (AES-256-GCM). By downloading or using this software, you agree to comply with all applicable export control laws and regulations in your jurisdiction. The authors make no representations regarding the legality of this software in any particular country.

## License

[MIT](LICENSE)
