#!/usr/bin/env python3
"""Audit the ORIGINAL APK's exact DEX entry points. Requires androguard==4.1.3."""
import argparse
import hashlib
import json
from pathlib import Path
from loguru import logger
logger.remove()
from androguard.core.apk import APK
from androguard.core.dex import DEX

TARGET = "Lcom/zte/cn/zteshare/ZteShareStatus;"
RUNNER = "Lcom/zte/cn/zteshare/ZteShareStatus$b;"
EXPECTED = "a65f3ebc49931f25e3d04da3e3c94743f996384b341db0a93bc02e45a8b1d840"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--json", type=Path)
    args = parser.parse_args()
    apk = APK(str(args.apk))
    dex_bytes = apk.get_dex()
    assert apk.get_package() == "com.zte.cn.zteshare"
    assert apk.get_androidversion_name() == "16.0.000.000.2607131724"
    assert hashlib.sha256(dex_bytes).hexdigest() == EXPECTED
    dex = DEX(dex_bytes)
    classes = {c.get_name(): c for c in dex.get_classes()}

    def method(cls, name, desc):
        return next(m for m in classes[cls].get_methods()
                    if m.get_name() == name and m.get_descriptor() == desc)

    def code(m):
        return "\n".join(i.get_name() + " " + i.get_output() for i in m.get_instructions())

    def callers(cls, name, desc):
        needle = cls + "->" + name + desc
        return sorted(c.get_name() + "->" + m.get_name() + m.get_descriptor()
                      for c in dex.get_classes() for m in c.get_methods()
                      if any(i.get_name().startswith("invoke-") and needle in i.get_output()
                             for i in m.get_instructions()))

    arm = code(method(TARGET, "F", "(Z)V"))
    assert "600000" in arm and "->b0(Ljava/lang/Runnable; J)V" in arm
    assert "->e0(Ljava/lang/Runnable;)V" in arm and "->c0(Landroid/content/Context; J)V" in arm
    assert "putInt" not in arm and "zte_share_enable" not in arm
    restore = code(method(TARGET, "z", "(Landroid/content/Context;)J"))
    assert '"time_switch_timeout"' in restore and "->getLong" in restore
    restore_callers = callers(TARGET, "z", "(Landroid/content/Context;)J")
    assert restore_callers == [TARGET + "->n(Landroid/content/Context;)V"]
    n = code(method(TARGET, "n", "(Landroid/content/Context;)V"))
    assert "->z(Landroid/content/Context;)J" in n and "600000" in n and "->b0" in n
    assert "->currentTimeMillis()J" in arm and "->currentTimeMillis()J" in n
    assert "->w Ljava/lang/Runnable;" in arm and "->w Ljava/lang/Runnable;" in n
    scheduler = code(method(TARGET, "b0", "(Ljava/lang/Runnable; J)V"))
    cancel = code(method(TARGET, "e0", "(Ljava/lang/Runnable;)V"))
    assert "->postDelayed(Ljava/lang/Runnable; J)Z" in scheduler
    assert "->removeCallbacks(Ljava/lang/Runnable;)V" in cancel
    constructors = [code(m) for m in classes[TARGET].get_methods() if m.get_name() == "<init>"]
    assert any(RUNNER in body and "->w Ljava/lang/Runnable;" in body for body in constructors)
    p_callers = callers(TARGET, "p", "()V")
    assert p_callers == [TARGET + "->h(Lcom/zte/cn/zteshare/ZteShareStatus;)V"]
    run_code = code(method(RUNNER, "run", "()V"))
    assert "->h(" in run_code  # Synthetic accessor delegates to p().
    accessor = code(method(TARGET, "h", "(Lcom/zte/cn/zteshare/ZteShareStatus;)V"))
    assert "->p()V" in accessor
    assert callers(TARGET, "h", "(Lcom/zte/cn/zteshare/ZteShareStatus;)V") == [RUNNER + "->run()V"]
    # The direct caller is the synthetic accessor in raw DEX, not the JADX simplification.

    entries = {
        "settings": ("Lcom/zte/cn/zteshare/activity/SettingsActivity;", "V0", "()V"),
        "tile": ("Lcom/zte/cn/zteshare/qstile/ZTEShareTileService;", "b", "()V"),
        "tools": ("Lcom/zte/cn/zteshare/SetZteShareExecutor;", "refeshConnective",
                  "(Lcom/zte/toolsdk/producer/ToolsExecutor$OnToolsResultListener;)V"),
    }
    for entry in entries.values():
        body = code(method(*entry))
        assert "->F(Z)V" in body and '"zte_share_enable"' in body and "->putIntForUser" in body
    deferred = code(method(TARGET, "q", "()V"))
    assert "->m Z" in deferred and '"zte_share_enable"' in deferred
    info = (Path(__file__).parent / "src/io/github/redmagic/sharekeepon/ModuleInfo.java").read_text()
    assert EXPECTED in info
    hooks = (Path(__file__).parent / "src/io/github/redmagic/sharekeepon/HookEntry.java").read_text()
    assert 'getDeclaredMethod("F", boolean.class)' in hooks
    assert 'getDeclaredMethod("z", Context.class)' in hooks
    assert 'getDeclaredMethod("n", Context.class)' in hooks
    assert 'getDeclaredMethod("run")' in hooks
    assert 'getDeclaredMethod("q"' not in hooks and 'getDeclaredMethod("t0"' not in hooks
    report = {"package": apk.get_package(), "version": apk.get_androidversion_name(),
              "dex_sha256": EXPECTED, "timer_ms": 600000,
              "stored_timer_read_callers": restore_callers,
              "wall_clock_start_and_dedicated_callback_verified": True,
              "auto_close_callers": p_callers,
              "manual_switch_entries": list(entries),
              "manual_deferred_close_preserved": True,
              "result": "PASS: static DEX audit; device behavior still requires testing"}
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if args.json:
        args.json.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
