# Agent Handoff — bark-to-pay (Ark Tap-to-Pay Wallet, Android PoC)

## Mission
Build an Android PoC app (**native Kotlin**, project name **bark-to-pay**) where two phones running the
same app can send/receive bitcoin over the **Ark protocol** by tapping phones together (**NFC**).
NFC only transports a payment URI; settlement is an **instant Ark out-of-round (arkoor) payment**
via Second's public Ark server.

## Confirmed product decisions
1. Phone-to-phone; the same app both sends and receives.
2. Both phones are assumed **online** (sender must be online for Ark; the receiver can even be offline
   at tap time — arkoor supports offline receiving via the server mailbox).
3. First target network: **signet** (free test coins, faucet at https://signet.2nd.dev/).
4. NFC payload: **BIP 321 `bitcoin:` URI** containing a fresh Ark address (+ amount + label).
5. Stack: **native Kotlin + Jetpack Compose** (decided over Expo/React Native to eliminate all
   third-party NFC bridge risk — `HostApduService` and `NfcAdapter` reader mode are first-party APIs).
6. Ark server: Second's public signet server:
   - Ark: `https://ark.signet.2nd.dev`
   - Esplora: `https://esplora.signet.2nd.dev`
7. HCE AID: **hardcoded `D2760000850101`** (standard NFC Forum Type 4 NDEF tag application).
   Protocol variant: **standards-based Type 4 NDEF tag emulation** (URI record) — recommended for
   future interop; fallback if device quirks appear: custom AID + trivial SELECT/GET-DATA APDU.
8. Amount is set by the **receiver** when generating the URI; payer only confirms.

## Architecture
```
PAYEE (receiver)                                PAYER (sender)
Compose form: amount + label                    "Tap to pay" screen → NFC reader mode
wallet.newAddress() → build BIP 321 URI   NFC   IsoDep APDUs: SELECT AID → read NDEF URI
BarkHceService (HostApduService)         ====>  parse BIP 321 → validateArkAddress()
  emulates NFC Type 4 NDEF tag (URI rec.)       confirm sheet → estimateArkoorPaymentFee()
notificationsFlow() → MovementCreated ✅        wallet.sendArkoorPayment(addr, sats) ✅
        └────────────► Ark signet server (arkoor, instant settlement) ◄────────────┘
```

## Verified stack
| Layer | Choice | Verified facts |
|---|---|---|
| Language/UI | Kotlin, Jetpack Compose, minSdk 24 | — |
| Wallet | `tech.second.bark:bark-android` from GitLab Maven `https://gitlab.com/api/v4/projects/78057981/packages/maven` | **Pinned `0.25.0+bark-0.7.1`** (latest at scaffold time; docs still show `0.9.0+bark.0.2.4` — the API changed, see below). Requires `net.java.dev.jna:jna:5.18.1@aar` (exclude transitive JNA jar) + `packaging { jniLibs.useLegacyPackaging = true }` or the app crashes with `UnsatisfiedLinkError`. |
| HCE (payee) | Own `HostApduService` (`BarkHceService`), Type 4 NDEF emulation, AID `D2760000850101`, content stored in `SharedPreferences` so it works with the app backgrounded | First-party Android API; no third-party lib |
| NFC reader (payer) | `NfcAdapter.enableReaderMode` + `IsoDep.transceive` (SELECT AID → SELECT NDEF file → READ BINARY) | First-party Android API |
| Secrets | `EncryptedSharedPreferences` (Keystore-backed) for the mnemonic | — |
| Background | `WorkManager` for periodic wallet sync | — |

## Bark Kotlin API (verified against `bark-android:0.25.0+bark-0.7.1` generated sources)
- `uniffi.bark.*`; wallet ops are **suspend functions** → call from coroutines (`Dispatchers.IO`).
- `generateMnemonic()`, `validateMnemonic(m)`, `validateArkAddress(addr)` (format-only).
- `Config(serverAddress, esploraAddress, …)` — **no `network` param anymore**; new params
  `userAgent: String?` and `vtxoKeyGapLimit: UInt?` (pass `userAgent = "bark-to-pay/…"`, rest nullable).
- **Wallet lifecycle changed**: `Wallet.create` is gone. Single entry point:
  `Wallet.open(network = Network.SIGNET, mnemonicOrSeed = m, config = cfg, args = WalletOpenArgs(runDaemon = true, datadir = …, onchain = null, createIfNotExists = true))`.
  `mnemonicOrSeed` accepts BIP-39 mnemonic or 64-byte hex seed.
- Sats are **`ULong`** (`balance.spendableSats`, `sendArkoorPayment(addr, amountSats: ULong)`,
  `estimateArkoorPaymentFee(amountSats: ULong): FeeEstimate{ grossAmountSats, feeSats, netAmountSats, vtxosSpent }`).
- Receive: `wallet.newAddress()`, `wallet.sync()`, `wallet.maintenance()`, `wallet.balance()` (sats as integer types; never Float/Double).
- Send: `wallet.estimateArkoorPaymentFee(amountSats)` then `wallet.sendArkoorPayment(arkAddress, amountSats)` (instant arkoor; exact suspend signatures to be confirmed in Phase 0 against the generated `uniffi.bark` sources — same core interface as the RN bindings, which expose `sendArkoorPayment(arkAddress: string, amountSats: bigint)`).
- Notifications: `tech.second.bark.notificationsFlow()` extension ships with the artifact:
  `wallet.notificationsFlow().collect { … }` with `WalletNotification.MovementCreated / MovementUpdated / ChannelLagging`.
- Later (trust-model hygiene): `wallet.refreshVtxos(...)` / `wallet.maintenanceRefresh()` converts received
  arkoor spend-VTXOs into trustless round VTXOs; `wallet.history()` for the activity list.

## BIP 321 payload
- Format: `bitcoin:?<hrp>=<arkAddress>&amount=<BTC-decimal>&label=<urlencoded>`
- Query key = the Ark address's bech32m HRP per BIP 321 (`ark` mainnet; expected `tark` on signet —
  **verify at runtime** from the `newAddress()` string prefix).
- `amount=` is **decimal BTC** — convert sats→BTC with integer math, 8 decimal places, no floats.
- Parser: case-insensitive scheme + keys, percent-decode values, ignore unknown keys unless
  `req-` prefixed (per BIP 321 forward-compat rules).
- No BIP-321 builder exists in the client bindings (only in barkd REST) — build/parse the URI manually.

## Build environment (this machine)
- Gradle wrapper `8.10.2` present (`./gradlew`). **System Java is 25 — too new for Gradle 8.**
  Run with JDK 17: `export JAVA_HOME=/tmp/opencode/jdk17` (Temurin 17 tarball; re-download if wiped)
  or Android Studio's bundled JBR once the project is opened there.
- `local.properties` → `sdk.dir=/home/tom/Android/Sdk`. AGP auto-installed `platforms;android-35`
  + `build-tools;34.0.0`; licenses accepted. AVD `Pixel_10` (API 37) exists for the emulator.
- **Phase 0 status: scaffold + spikes implemented, `assembleDebug` green, unit tests green.
  Remaining: physical-device runs (faucet-fund + two-phone NFC tap). Emulator has NO NFC.**
- **Design PoC implemented (see `DESIGN.md`):** Compose Navigation (`onboarding`/`home`/`receive`/`pay`),
  bark palette M3 theme, `WalletRepository` (StateFlows + `notificationsFlow` → balance refresh /
  receive ack), `EncryptedSharedPreferences` mnemonic store, hardcoded signet `wallet/WalletConfig.kt`,
  pure-Kotlin `bip321/Bip321.kt` (12 unit tests), QR fallback on receive (ZXing), confirm bottom sheet
  + `sendArkoorPayment` on pay. Not yet done: history UI,
  `maintenanceRefresh()` policy, settings/backup screen.
- **Phase 1 done:** `INTERNET` + `ACCESS_NETWORK_STATE` permissions (their absence caused the
  "failed to connect to Ark server" error — every socket was denied). Mnemonic persisted only after
  a successful `Wallet.open` (failed create can't wedge the app). Open retry/backoff (3 attempts,
  exponential) + `Connectivity` helper (offline fail-fast, friendly error strings). Home ERROR state:
  Retry + Reset wallet (confirm dialog; wipes mnemonic + datadir, back to onboarding).
  `sync/SyncWorker.kt`: WorkManager periodic sync every 30 min, network constraint, KEEP policy,
  scheduled after each successful open, cancelled on reset; `WalletRepository.init` is idempotent
  so the worker can cold-start the process.
- **Normal receive added:** Receive screen has two modes — "Ready for tap" (NFC/BIP 321, amount
  required) and "Show address instead" (no NFC: QR + copyable/shareable Ark address, amount
  optional). Faucet `https://signet.2nd.dev/` pays directly to Ark addresses (VTXO payout),
  so no boarding flow needed for funding. On-chain boarding (`boardAll`/`boardFundingAddress`)
  deferred until on-chain interop is actually required.
- **Payer QR scanning added:** Pay screen arms camera + NFC simultaneously (whichever fires first);
  CameraX `PreviewView` + ML Kit `barcode-scanning` (QR only) in `ui/pay/QrScanner.kt`, latch-once,
  released on leaving the scanning state; `CAMERA` permission requested per visit, denial falls back
  to NFC-only UI. Enables full E2E with one physical phone (payer) + emulator (payee QR).
- **16 KB page-size compat:** CameraX pinned to **1.5.3** (≥1.4.2 ships 16 KB-aligned
  `libimage_processing_util_jni.so`; 1.6.x requires compileSdk 36 + AGP 8.9.1 — not yet).
  All 64-bit APK `.so`s verified LOAD-aligned ≥0x4000 (arm64 + x86_64). Re-check after any
  native-carrying dependency change.
- **Phase 4 done:** `WalletRepository.history` (StateFlow, `wallet.history()` newest-first, refreshed
  with balance everywhere) + Home "Activity" list via pure `ui/home/MovementUiMapper.kt`
  (7 unit tests; incoming non-final shown as "settling"). `maintenanceRefresh()` policy: fire-and-forget
  after incoming arkoor `MovementCreated` (coalesced via AtomicBoolean) + awaited inside `SyncWorker`.
- **Pre-flight NFC hardening done:** reader mode now polls NFC-A **and** NFC-B
  (`FLAG_READER_NFC_B` — some devices route HCE over NFC-B only; silent "no reaction" otherwise).
  Receive tap-mode checks payee-side NFC availability (`nfc/NfcAvailability.kt`, re-checked on
  ON_RESUME): NFC off → warning + "Open NFC settings" deep link; no hardware → "use the QR code".
  Ready for the two-physical-phone E2E test.
- **Manual send + close buttons added:** Pay screen has "Paste address instead" (both camera and
  NFC-only branches) → `State.ManualEntry` form (address/link field with clipboard Paste, amount
  field auto-filled + locked when the pasted URI carries an amount). `Bip321.parseAddressOrUri`
  (pure, 4 new tests → 16 total) accepts a raw Ark address or a `bitcoin:` URI; submit validates
  with `validateArkAddress`, estimates the fee, then reuses the normal Confirm sheet → send flow.
  Side effect: amount-less address QRs (own address-mode) are now payable by pasting + amount.
  Pay/Receive top bars use an explicit ✕ Close icon (was back arrow) that exits to Home from
  any state. Main pages (Receive amount form, Pay scanning — camera and NFC-only branches) also
  have an in-content "Back" button to Home, matching the sub-pages' affordances.
  Release APK rebuilt with these changes.
- **Bark sound added:** `res/raw/bark.ogg` (0.68 s single bark, 11 KB, Stored uncompressed) plays
  once on both success screens — payee "Received!" and payer "Sent!" — via
  `ui/common/BarkSound.kt` `playBark()` (MediaPlayer, USAGE_NOTIFICATION_EVENT so it respects
  silent/DND, auto-release, runCatching so audio can never crash a payment screen).
  **Attribution:** "Barking of a dog" by Amada44, CC BY-SA 3.0, via Wikimedia Commons
  (https://commons.wikimedia.org/wiki/File:Barking_of_a_dog.ogg), trimmed to 0.745–1.425 s with
  ffmpeg edge fades. The trimmed adaptation remains CC BY-SA 3.0.
- **One-tap pay (demo mode):** NFC tap now sends immediately — no confirm sheet. `PayViewModel`:
  shared `parseRequest()` + `send()`; `onTapRead` parses/validates **synchronously** and sets
  `State.Sending` before returning (a tag re-discovery while phones touch can never double-send).
  QR scan (`onUriRead`) and pasted address (`onManualSubmit`) keep the Confirm sheet with fee.
   `State.Confirming`/`ConfirmPaymentSheet` kept in code — re-enable tap confirmation by wiring
   the NFC reader back to `onUriRead`. Note: tap while wallet still opening now lands on the Error
   screen (was a small read-hint).
- **Receive-side sync pulse:** the daemon's default sync interval is 60 s
  (`daemonSyncIntervalSecs` unset), which delayed the payee's "Received!" screen by up to a
  minute whenever the server-push mailbox stream wasn't delivering. While armed
  (`State.Active`/`State.Address`) `ReceiveViewModel` now calls the lightweight
  `WalletRepository.sync()` (mailbox pull, no VTXO refresh) every 2 s; the pulse self-terminates
  on leaving those states and is cancelled on `backToEditing()` (together with `watchJob`).
- **Release build set up:** signing via `keystore.properties` (gitignored) + `keystore/bark-to-pay.jks`
  (PKCS12, RSA-2048, alias `bark-to-pay`); `signingConfigs.release` wired in `app/build.gradle.kts`,
  minify stays OFF (bark's JNA/UniFFI reflection would break under R8). Build:
  `export JAVA_HOME=/tmp/opencode/jdk17 && ./gradlew :app:assembleRelease` →
  `app/build/outputs/apk/release/app-release.apk` (signed, verified with `apksigner`; all 64-bit
  `.so` PT_LOAD segments 16 KB-aligned — parser reads `p_align` at ELF64 offset 0x30 / ELF32 0x1C).
  Release is non-debuggable; `adb logcat` still works over USB debugging if a phone gets connected.
  Release ≠ debug signature → installing over a debug build requires uninstall (wipes wallet).

## Implementation phases
- **Phase 0 — Scaffold + two spikes (de-risk first):**
  1. Android Studio project `bark-to-pay` (Kotlin, Compose, minSdk 24).
  2. Gradle: GitLab Maven repo + `bark-android` + `jna@aar` + `useLegacyPackaging`.
  3. **Spike A (wallet):** create wallet on signet → `newAddress()` → faucet-fund → `sync()` → balance.
     Validates native-lib loading on real devices.
  4. **Spike B (NFC):** `BarkHceService` emitting a static BIP 321 URI + reader screen
     (`enableReaderMode` + `IsoDep`) — verify phone-to-phone transfer before any payment code.
- **Phase 1 — Wallet layer:** onboarding (create/restore), mnemonic in `EncryptedSharedPreferences`,
  `WalletRepository` singleton with dedicated `CoroutineScope`, `WorkManager` periodic sync.
- **Phase 2 — Receive ("Tap to receive"):** Compose form → `newAddress()` → BIP 321 URI → publish to
  `BarkHceService` via `SharedPreferences` → enable emulation → read-event feedback → fresh address per
  request → disable on screen exit.
- **Phase 3 — Pay ("Tap to pay"):** reader mode while pay screen is foreground → read NDEF → parse URI →
  validate → confirm sheet (amount, label, fee estimate) → `sendArkoorPayment` → success/failure UI.
- **Phase 4 — Settlement UX:** collect `notificationsFlow()` in wallet ViewModel → on `MovementCreated`
  refresh balance + history; show received payments as "pending refresh" (temporary arkoor trust model)
  with automatic `maintenanceRefresh()` policy.
- **Phase 5 — E2E on signet:** faucet → `boardAmount` → tap-to-pay between two physical devices →
  assert balances + history on both. Unit tests for the BIP 321 builder/parser (pure JVM tests).

## AndroidManifest requirements
- `<uses-permission android:name="android.permission.NFC" />`
- `<uses-feature android:name="android.hardware.nfc.hce" android:required="false" />` (false so the app
  still installs on NFC-less devices; gate the feature at runtime)
- `BarkHceService`: `android:exported="true"`,
  `android:permission="android.permission.BIND_NFC_SERVICE"`, intent-filter
  `android.nfc.cardemulation.action.HOST_APDU_SERVICE`, meta-data
  `android.nfc.cardemulation.host_apdu_service` → `@xml/aid_list` with AID `D2760000850101`
  in `aid-group` category `other`, `requireDeviceUnlock=false`.

## Risks / notes
- Reader phone must have the pay screen foregrounded; payee works background/locked
  (`requireDeviceUnlock=false`, configurable).
- Fresh Ark address per payment request (no reuse).
- Received arkoor VTXOs carry a temporary sender+server-collusion trust assumption until refreshed
  (28-day VTXO lifetime on Second's server) — UI must reflect this honestly.
- Possible AID conflicts with other HCE apps are rare for the NDEF Tag AID; document the Android
  Settings workaround.
- Device-specific NFC quirks are the main unknown — surfaced by Spike B on day one.
- iOS is out of scope (no HCE on iPhones). Mainnet, self-hosted Ark server, Lightning fallback
  (`lightning=` param via `wallet.bolt11Invoice(amountSats)`), and fully-offline payments
  (impossible on Ark — sender must reach the server) are out of scope.

## Key references
- Bark docs: https://second.tech/docs (Kotlin guide, connection details, learn/intro, learn/payments)
- Kotlin bindings repo + example app: https://gitlab.com/ark-bitcoin/bark-ffi-bindings (`kotlin/example`)
- BIP 321: https://bips.dev/321
- Android HCE: `HostApduService` docs; NFC Forum Type 4 tag operation spec
