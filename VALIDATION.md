# Validation — 1.0

## Passed

- Original target APK and DEX fingerprint audit: package/version, the dedicated 600000 ms timer,
  stored-timer restoration, expiry callback, and independent manual-close paths.
- 28 host-JVM Hook assertions: exact-version gate, mismatched-code rejection, rollback,
  enabled/disabled behavior for all three hooks, timer cancellation when enabling,
  original timer rearming when disabling, and no timer action while sharing is off.
- Five Robolectric 4.14.1 / Android API 35 tests: exactly two switches, persistent module state,
  activity destruction/recreation, night mode, latest-release update intent,
  read-only external configuration, and restoration of the last confirmed share state if the backend disappears.
- Java/DEX/resource compilation, zip alignment, APK v3 signature, and same-certificate upgrade
  from earlier test builds. Display version 1.0 uses versionCode 100.
- Command/state broadcasts require the module's signature permission. The external configuration
  provider exposes only an enabled bit and rejects every write method.
- The Xposed API is compile-only. No original system APK, private key or build cache is published.

The fixed launcher styles system bars only after receiving view insets. The original premature
PhoneWindow controller access was reproduced on Android framework code and removed in the test fixes.

## Reproduce

```bash
python3 build.py
python3 verify_hooks.py --tools .build-tools --apk /path/to/original-share.apk
python3 -m pip install androguard==4.1.3
python3 verify_target.py /path/to/original-share.apk
python3 verify_startup.py --tools .build-tools
```

The activity regression requires Maven 3.9+ and downloads Robolectric and the Android framework runtime.
The original target APK must be supplied by its owner and is not redistributed.

## Device verification

No NX789J device is connected to the build environment. LSPosed injection, signature-protected
cross-process control, the vendor service, idle/screen-off discovery and actual file transfers must
still be verified on the phone. JVM fixtures and Robolectric do not emulate the vendor radio stack.

Suggested acceptance: enable the framework scope and reboot; test both switches; leave sharing
on for 15 minutes; verify discovery and receiving from another device; disable the module and
confirm the original timeout returns; test manually turning sharing off.
