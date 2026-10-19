# Validation — 1.1

## Passed

- Original target APK/DEX audit: exact package, version and fingerprint; dedicated timeout
  runnable; wall-clock start persistence; native scheduling/cancellation methods; separate
  fresh-start and restored-start 600000 ms arithmetic; independent manual close and transfer paths.
- 81 host-JVM hook/timer assertions using the actual HookEntry and a vendor timer-contract
  fixture: exact-DEX gate, mismatched-code rejection, partial-install rollback, every preset,
  native initialization/persistence, expiry policy, manual cancellation, elapsed-time recovery,
  expired starts, wall-clock rollback, 30/60-minute recovery past the old 10-minute boundary,
  always-on cancellation, disabled-module stock timing, and preset changes while sharing is off.
- Eight Robolectric 4.14.1 / Android API 35 tests: two switches and persistent module state;
  recreation; night mode; matching version/update card and both browsable links; read-only
  configuration; last confirmed share state on backend loss; all six preset selections and
  persistence after restart; rejection of invalid settings; landscape scroll access.
- APK compilation, DEX/resources, zip alignment, v3 signature and same-certificate upgrade.
  Display version 1.1 uses versionCode 110. Certificate SHA-256:
  `bf115fcf24f6365717149ba614f40967928077f07a16f4e3c11dde69dbd99a8d`.
- Scoped command/state broadcasts still require the module's signature permission.
  The provider exposes only enabled state and a validated timeout preset, and rejects writes.
- No new Android permissions. Xposed API remains compile-only. No original system APK,
  vendor implementation, private keystore or build cache is in the public source package.

The stock dedicated timeout runnable still performs the actual closing operation, retaining
its handling for active transfers. Fresh timers preserve stock initialization before replacing
only that runnable's delay. Restored timers use the stock saved start time and the selected
full duration, without the original 10-minute clamp. The first configuration read deliberately
does not reset an already-running timer.

Changing a preset while sharing is on starts a new full interval. Changing it while sharing
is off saves the selection without enabling sharing. Upgrading from 1.0 defaults to always-on.

## Reproduce

```bash
python3 build.py
python3 verify_hooks.py --tools .build-tools --apk /path/to/original-share.apk
python3 -m pip install androguard==4.1.3
python3 verify_target.py /path/to/original-share.apk
python3 verify_startup.py --tools .build-tools
```

The Android regression requires Maven 3.9+ and downloads Robolectric and an Android framework
runtime. The original target APK is supplied by its owner and is not redistributed.
Machine-readable static/APK reports and test summaries are under `validation/`.

## Device verification

No NX789J is connected to this build environment. These tests do not verify LSPosed/Vector
injection, cross-process IPC on the actual firmware, the vendor service, wireless discovery,
screen-off timing or actual file transfers. Android API 35 framework tests are not an
Android 16 / vendor-ROM device test.

After installing/upgrading, reboot. On the phone verify both switches, every timeout preset,
manual cancellation, a process restart partway through a 30/60-minute interval, changing
presets while sharing is on/off, module disable restoring stock timing, and receiving files
while a timeout expires. Verify that changing to always-on removes the pending timer.
