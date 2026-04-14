# Contributing to Pionen

Thank you for your interest in contributing! This is a security-sensitive project — please read this guide carefully before submitting changes.

---

## Code of Conduct

Be respectful, constructive, and professional. Security discussions are welcome; please avoid sharing exploit details in public issues (see [SECURITY.md](SECURITY.md) instead).

---

## Ways to Contribute

- 🐛 **Bug reports** — Open an issue using the Bug Report template
- 💡 **Feature requests** — Open an issue using the Feature Request template
- 🔒 **Security vulnerabilities** — **Do NOT open a public issue.** See [SECURITY.md](SECURITY.md)
- 🔧 **Code contributions** — Fork → branch → PR (see below)
- 📖 **Documentation** — Typos, clarity, added examples are always welcome

---

## Development Setup

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK API 26+

### Clone & Open
```bash
git clone https://github.com/YOUR_USERNAME/pionen.git
```
Open the project root in Android Studio.

### Build Debug
```bash
./gradlew assembleDebug
```

### Run Tests
```bash
./gradlew test
./gradlew connectedAndroidTest
```

---

## Pull Request Process

1. **Fork** the repository and create a feature branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Write code** — follow the conventions below.

3. **Test your changes** — run unit tests and, if possible, deploy on a physical device.

4. **Update documentation** — update the README or inline KDoc if your change is user-visible.

5. **Open a PR** against `main` with a clear title and description:
   - What problem does it solve?
   - How was it tested?
   - Any known limitations?

6. Address review comments promptly.

---

## Code Conventions

### Kotlin
- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `kotlin.code.style=official` (already set in `gradle.properties`)
- Prefer `sealed class` over `enum` for state machines
- Prefer `StateFlow` / `Flow` over callbacks

### Security-Sensitive Code
- Any changes to `core/crypto/`, `core/security/`, or `di/AppModule.kt` require extra scrutiny
- New cryptographic implementations must cite their security rationale in KDoc
- Never log sensitive data — use `SecureLogger` throughout
- All encryption must use the `KeyManager` / `FileEncryptor` pipeline — no ad-hoc crypto

### Architecture
- Follow MVVM: Screens → ViewModels → Repositories / UseCases → Core
- Use Hilt `@Inject` — avoid manual instantiation of dependencies
- `@Singleton` for stateful managers; `@ViewModelScoped` for ViewModels

### Commit Style
Use [Conventional Commits](https://www.conventionalcommits.org/):
```
feat: add decoy vault PIN support
fix: correct IV read length in FileEncryptor.decryptStream
security: use EncryptedSharedPreferences for database key storage
docs: update README Tor integration section
refactor: extract GCM cipher init into helper
```

---

## What We Won't Merge

- Changes that weaken security (e.g. disabling encryption, weakening PBKDF2 iterations)
- Hard-coded secrets, API keys, or passwords
- Dependencies from untrusted or unofficial sources
- Code that intentionally bypasses Android OS security features
- Breaking changes without a migration path documented in the PR

---

## Questions?

Open a [GitHub Discussion](../../discussions) for general questions.
