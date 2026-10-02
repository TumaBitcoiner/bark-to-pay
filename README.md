# bark-to-pay

Tap-to-pay bitcoin over the **Ark protocol**. Two Android phones, one tap — NFC hands over a
BIP 321 payment request, settlement is an instant Ark out-of-round (arkoor) payment. A dog barks
when the money lands.

**Proof of concept on signet** — not for real funds.

## Features

- **Tap to pay** — payee shows a request (NFC HCE Type 4 tag emulation), payer taps, confirms, done.
- **QR codes** — same payment request shown as a QR on the receive side; camera scanner built into
  the pay side. Works one-phone + emulator, no NFC needed.
- **Paste an address** — send to any Ark address or `bitcoin:` payment link by pasting it.
- **Plain-address receive** — copy/share a fresh Ark address (e.g. for the signet faucet).
- **History** — Home shows recent movements; incoming payments show as "settling" until a round
  confirms them.
- **Dog bark** on every successful send *and* receive (notification volume, respects silent/DND).

## Tech

- Native Kotlin + Jetpack Compose (Material 3), minSdk 24, single module.
- [bark-android](https://gitlab.com/ark-bitcoin/bark) `0.25.0+bark-0.7.1` (UniFFI bindings) — wallet
  runs in-process against Second's public signet servers (`ark.signet.2nd.dev`,
  `esplora.signet.2nd.dev`).
- NFC: `HostApduService` (HCE, AID `D2760000850101`) ↔ `NfcAdapter` reader mode + IsoDep APDUs.
- BIP 321 `bitcoin:` URIs carry the payment request (address + amount + label).
- CameraX + ML Kit for QR scanning, ZXing for QR rendering, WorkManager for background sync,
  EncryptedSharedPreferences for the mnemonic.

## Build

Requires JDK 17 (Gradle 8.10) and the Android SDK:

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # unit tests (29)
./gradlew :app:assembleRelease      # signed release APK (see below)
```

### Release signing

Release builds are signed with a local keystore that is **not** in this repo. To reproduce:

```bash
keytool -genkeypair -keystore keystore/bark-to-pay.jks -alias bark-to-pay \
  -keyalg RSA -keysize 2048 -validity 10000
```

then create `keystore.properties` (gitignored) at the repo root:

```properties
storeFile=keystore/bark-to-pay.jks
keyAlias=bark-to-pay
storePassword=...
keyPassword=...
```

## Install & try it

1. Copy `app/build/outputs/apk/release/app-release.apk` to two Android phones and install
   (allow "Install unknown apps" — no developer options needed).
2. Create a wallet on each (needs internet).
3. Fund one of them: Receive → "Show address instead" → copy → paste into the
   [signet faucet](https://signet.2nd.dev/) (choose an Ark payout).
4. Payee: Receive → amount ≥ 1,000 sats → "Ready for tap". Payer: Pay → hold phones together
   (or scan the QR) → Confirm & pay. Both phones bark. 🐕

Note: the emulator has no NFC — tap-to-pay needs two physical devices.

## Project layout

```
app/src/main/java/tech/second/barktopay/
├── MainActivity.kt          # NavHost: onboarding / home / receive / pay
├── wallet/                  # WalletRepository (bark SDK), config, mnemonic store, connectivity
├── nfc/                     # HCE service, Type 4 NDEF codec, reader controller, availability
├── bip321/                  # pure-Kotlin BIP 321 URI builder/parser (unit-tested)
├── sync/                    # WorkManager periodic sync
└── ui/                      # Compose screens: onboarding, home, receive, pay, common
```

See `DESIGN.md` for the UI contract and `PLAN.md` for the build plan, decisions, and status.

## Attribution

- `app/src/main/res/raw/bark.ogg` — ["Barking of a dog"](https://commons.wikimedia.org/wiki/File:Barking_of_a_dog.ogg)
  by Amada44, [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0), trimmed. The trimmed
  adaptation remains CC BY-SA 3.0.

## Disclaimer

Experimental signet software. Ark signet VTXOs carry a sender+server-collusion trust assumption
until refreshed in a round. Do not use with real funds.
