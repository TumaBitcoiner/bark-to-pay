# bark-to-pay — Design (PoC)

Basic, usable tap-to-pay over Ark. Technical details live in `PLAN.md`; this file is the UI/UX contract.

## Locked decisions
- **Scope:** wallet creation (create/restore) + receive/pay. No settings screen, no fiat, no history detail for now.
- **Amounts:** integer sats only. No decimals, no fiat conversion.
- **Receive screen:** NFC HCE + **QR fallback** of the same BIP 321 URI.
- **Theme:** fixed bark palette (amber/brown seed), Material 3, light + dark. No dynamic color.
- **Network:** signet, hardcoded in `wallet/WalletConfig.kt`. Not exposed in UI.
- **Trust honesty:** arkoor-received funds are shown with a "settling" hint until refreshed into round VTXOs.

## Screens

### Onboarding (first launch only)
- `Create wallet` — generates mnemonic, stores encrypted, opens wallet, straight to Home.
- `Restore` — paste mnemonic → validate → open.
- Backup is not forced in the PoC.

### Home
- Big balance (sats, thousands separators), auto-refresh on resume.
- Two large actions: **Receive** / **Pay**.
- Activity list: movements newest-first; `+`/`-` sats, kind, time, status. Incoming payments
  that haven't reached a final state are labelled **"settling"** (trust honesty).

### Receive
1. Form: amount (sats), label (optional) → two actions:
   - `Ready for tap` (amount required): fresh Ark address → BIP 321 URI → published to HCE;
     pulsing NFC visual + QR of the URI. If NFC is off/unavailable: warning + settings deep link
     (QR keeps working regardless).
   - `Show address instead` (amount optional): no NFC — QR of the address URI + selectable
     monospace address + Copy / Share buttons. This is the faucet/regular-sender flow; the
     signet faucet pays directly to Ark addresses.
2. On incoming movement notification: "Received!" state, balance refreshes (both modes).
   A dog bark plays once on the success screen (notification volume, respects silent/DND).
   While armed, the screen pulses the lightweight `wallet.sync()` every 2 s so the incoming
   arkoor is detected in ~2–4 s instead of up to the daemon's 60 s sync interval.
3. Leaving the screen clears any published HCE payload.

### Pay
1. Open screen → NFC reader armed **and** camera QR scanner active (permission asked once per visit;
   denial → NFC-only UI). Whichever delivers a request first wins. "Paste address instead" opens a
   manual form (Ark address or `bitcoin:` link + amount; URI-carried amounts are locked in) that
   reuses the same confirm sheet.
2. On tap/scan: read URI → parse BIP 321 → validate Ark address.
3. **NFC tap sends immediately** (demo mode, one-tap UX; the state transition to "Sending…" is
   synchronous so a tag re-discovery can't double-send). QR scan and pasted addresses still get a
   confirm bottom sheet: amount, label, fee estimate → `Confirm` → `sendArkoorPayment`.
   Re-enabling tap confirmation = wire the NFC reader back to `onUriRead` (sheet code kept).
4. Result: "Sent!" or plain-language error (NFC off, no NFC hardware, invalid URI, insufficient funds, server unreachable).

## Architecture
- Single `MainActivity` + Compose Navigation (`onboarding` / `home` / `receive` / `pay`).
- MVVM: `WalletRepository` singleton owns the `Wallet` and exposes `StateFlow`s; per-screen ViewModels.
- HCE payload handoff stays via `SharedPreferences` (`HcePayloadStore`) so receive works with the app backgrounded.
- BIP 321 builder/parser is a pure-Kotlin object with JVM unit tests.
