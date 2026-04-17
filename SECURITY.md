# Security Policy

## Supported Versions

| Version | Supported |
|---|---|
| 1.x (latest) | ✅ Yes |

## Reporting a Vulnerability

**Please do NOT open a public GitHub issue for security vulnerabilities.**

Instead, report them privately:

1. **GitHub Private Vulnerability Reporting** *(preferred)*  
   Go to → **Security** tab → **Report a vulnerability** → Fill in the form.

2. **Email**  
   Send details to: `support@pionen.in`  
   Encrypt with PGP if possible (key available on request).

### What to include

- A clear description of the vulnerability
- Steps to reproduce
- Impact assessment (what an attacker can achieve)
- Suggested fix (optional but appreciated)

### What to expect

- **Acknowledgement** within 48 hours
- **Status update** within 7 days
- **Fix timeline** depends on severity — critical issues targeted within 14 days

### Scope

In scope:
- Encryption / key management bypass
- Authentication bypass (PIN, biometric)
- Unintended data leakage to disk, logs, or backups
- Privilege escalation within the app

Out of scope:
- Issues requiring physical access to an already-unlocked, unencrypted device
- Basic Android OS vulnerabilities not specific to Pionen
- Social engineering

## Security Design Principles

- All vault content encrypted with AES-256-GCM before touching disk
- Per-file unique keys stored in Android Keystore (hardware-backed on supported devices)
- PIN hashed with PBKDF2-SHA256 (100,000 iterations, 256-bit key, 32-byte salt)
- Crypto-shredding for instant irrecoverable deletion
- No cloud backup (`allowBackup=false`, all extraction rules blocked)
- `FLAG_SECURE` prevents screenshots and recent-app thumbnails
- Auto-lock on background
