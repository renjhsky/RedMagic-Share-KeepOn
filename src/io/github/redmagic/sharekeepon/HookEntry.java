package io.github.redmagic.sharekeepon;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.TextView;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** All hooks are process-local and gated by the supplied target's DEX hash. */
public final class HookEntry implements IXposedHookLoadPackage {
    private static final AtomicInteger arms = new AtomicInteger();
    private static final AtomicInteger restores = new AtomicInteger();
    private static final AtomicInteger callbacks = new AtomicInteger();
    private static boolean entered;
    private static volatile int coreHooks;
    private static volatile boolean summaryHook;
    private static volatile boolean receiverReady;
    private static volatile String fingerprint = "";
    private static volatile String failure = "";
    private static volatile long onSince;
    private static volatile boolean enabled = true;
    private static ClassLoader targetLoader;
    private static final Map<TextView, CharSequence> originalSummaries = new WeakHashMap<>();

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!ModuleInfo.TARGET.equals(p.packageName)
                || !ModuleInfo.TARGET.equals(p.processName) || !p.isFirstApplication) return;
        synchronized (HookEntry.class) {
            if (entered) return;
            entered = true;
        }
        targetLoader = p.classLoader;
        try {
            fingerprint = dexHash(p.appInfo.sourceDir);
            if (ModuleInfo.TARGET_DEX_SHA256.equals(fingerprint)) {
                installCore(p.classLoader);
                installSummary(p.classLoader);
            } else {
                failure = "互传 APK 与适配版本不一致，未修改计时逻辑。";
            }
        } catch (Throwable e) {
            failure = "计时拦截未安装：" + e.getClass().getSimpleName();
            log(failure);
        }
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        Context c = (Context) param.args[0];
                        if (c != null && ModuleInfo.TARGET.equals(c.getPackageName())) registerProbe(c);
                    }
                });
        } catch (Throwable e) {
            log("诊断入口注册失败：" + e.getClass().getSimpleName());
        }
        log("version=" + ModuleInfo.VERSION + " coreHooks=" + coreHooks + " " + failure);
    }

    private static void installCore(ClassLoader loader) throws Throwable {
        Class<?> status = XposedHelpers.findClass(ModuleInfo.STATUS_CLASS, loader);
        Class<?> runner = XposedHelpers.findClass(ModuleInfo.CALLBACK_CLASS, loader);
        Method arm = status.getDeclaredMethod("F", boolean.class);
        Method restore = status.getDeclaredMethod("z", Context.class);
        Method run = runner.getDeclaredMethod("run");
        if (arm.getReturnType() != void.class || restore.getReturnType() != long.class
                || run.getReturnType() != void.class || !Runnable.class.isAssignableFrom(runner)
                || runner.getDeclaredField("a").getType() != status) {
            throw new IllegalStateException("target signatures changed");
        }
        List<XC_MethodHook.Unhook> installed = new ArrayList<>();
        try {
            installed.add(XposedBridge.hookMethod(arm, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!enabled) return;
                    if (Boolean.TRUE.equals(param.args[0])) {
                        arms.incrementAndGet();
                        // F controls the timer ONLY. Its false branch cancels the dedicated
                        // runnable and clears the saved start time; it never changes the switch.
                        param.args[0] = false;
                    }
                }
            }));
            installed.add(XposedBridge.hookMethod(restore, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!enabled) return;
                    if (!param.hasThrowable() && param.getResult() instanceof Long) {
                        if ((Long) param.getResult() > 0L) restores.incrementAndGet();
                        // z(Context) reads only time_switch_timeout. Returning zero prevents
                        // n(Context) from rearming an old timer after a process restart.
                        param.setResult(0L);
                    }
                }
            }));
            installed.add(XposedBridge.hookMethod(run, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!enabled) return;
                    callbacks.incrementAndGet();
                    // This exact runnable has one job: call the idle auto-close routine p().
                    // Manual close and transfer-completion cleanup do not call this runnable.
                    param.setResult(null);
                }
            }));
            coreHooks = installed.size();
        } catch (Throwable e) {
            for (XC_MethodHook.Unhook u : installed) u.unhook();
            coreHooks = 0;
            throw e;
        }
    }

    private static void installSummary(ClassLoader loader) {
        if (coreHooks != 3) return;
        try {
            XposedHelpers.findAndHookMethod(ModuleInfo.SETTINGS_CLASS, loader, "onResume",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        Activity a = (Activity) param.thisObject;
                        android.view.View v = a.findViewById(ModuleInfo.SUMMARY_VIEW_ID);
                        if (v instanceof TextView) {
                            TextView text = (TextView) v;
                            if (!originalSummaries.containsKey(text)) originalSummaries.put(text, text.getText());
                            text.setText(enabled ? "开启后可被附近的设备发现，将保持开启直至手动关闭" : originalSummaries.get(text));
                        }
                    }
                });
            summaryHook = true;
        } catch (Throwable e) {
            log("设置页说明更新失败：" + e.getClass().getSimpleName());
        }
    }

    private static synchronized void registerProbe(final Context context) {
        if (receiverReady) return;
        try {
            Handler handler = new Handler(Looper.getMainLooper());
            refreshConfig(context, false);
            BroadcastReceiver probe = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent intent) {
                    boolean control = ModuleInfo.ACTION_CONTROL.equals(intent.getAction());
                    if (!control && !ModuleInfo.ACTION_PROBE.equals(intent.getAction())) return;
                    String commandError = "";
                    if (control && coreHooks == 3) {
                        try {
                            String operation = intent.getStringExtra("operation");
                            if ("config".equals(operation)) refreshConfig(context, false);
                            else if ("share".equals(operation)) setShare(context, intent.getBooleanExtra("value", false));
                        } catch (Throwable e) {
                            commandError = "操作失败，请重新打开红魔互传后重试";
                            log("控制失败：" + e.getClass().getSimpleName());
                        }
                    }
                    Bundle b = new Bundle();
                    b.putString("module", ModuleInfo.VERSION);
                    b.putString("target", targetVersion(context));
                    b.putString("fingerprint", fingerprint);
                    b.putString("failure", failure);
                    b.putInt("hooks", coreHooks);
                    b.putBoolean("enabled", enabled);
                    b.putBoolean("matched", ModuleInfo.TARGET_DEX_SHA256.equals(fingerprint));
                    b.putString("error", commandError);
                    b.putBoolean("summary", summaryHook);
                    b.putInt("pid", Process.myPid());
                    b.putInt("arms", arms.get());
                    b.putInt("restores", restores.get());
                    b.putInt("callbacks", callbacks.get());
                    int state = shareState(context);
                    if (state != 1) onSince = 0;
                    else if (onSince == 0) onSince = SystemClock.elapsedRealtime();
                    b.putInt("switch", state);
                    b.putLong("observed", onSince == 0 ? 0L : SystemClock.elapsedRealtime() - onSince);
                    setResultCode(Activity.RESULT_OK);
                    setResultExtras(b);
                }
            };
            IntentFilter filter = new IntentFilter(ModuleInfo.ACTION_PROBE);
            filter.addAction(ModuleInfo.ACTION_CONTROL);
            // Only this module's signing identity may send commands or inspect its live state.
            context.registerReceiver(probe, filter, ModuleInfo.PERMISSION_CONTROL, handler, Context.RECEIVER_EXPORTED);
            receiverReady = true;
            context.getContentResolver().registerContentObserver(ModuleConfig.URI, false, new ContentObserver(handler) {
                @Override public void onChange(boolean selfChange) { refreshConfig(context, false); }
            });
            // Observe real switch changes without polling, alarms, wake locks, or a root daemon.
            try {
                onSince = shareState(context) == 1 ? SystemClock.elapsedRealtime() : 0;
                context.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor("zte_share_enable"), false,
                    new ContentObserver(handler) {
                        @Override public void onChange(boolean selfChange) {
                            if (shareState(context) == 1) {
                                if (onSince == 0) onSince = SystemClock.elapsedRealtime();
                            } else onSince = 0;
                        }
                    });
            } catch (Throwable e) { log("开关状态观察失败：" + e.getClass().getSimpleName()); }
        } catch (Throwable e) { log("诊断接收器不可用：" + e.getClass().getSimpleName()); }
    }

    private static void refreshConfig(Context context, boolean force) {
        boolean desired = ModuleConfig.readFromModule(context);
        boolean changed = enabled != desired;
        enabled = desired;
        if (coreHooks == 3 && (changed || force) && shareState(context) == 1) {
            try {
                Object status = XposedHelpers.callStaticMethod(
                        XposedHelpers.findClass(ModuleInfo.STATUS_CLASS, targetLoader), "v");
                XposedHelpers.callMethod(status, "G", context);
                applyTimerPolicy(status, true);
            } catch (Throwable e) { log("同步计时策略失败：" + e.getClass().getSimpleName()); }
        }
    }

    private static void applyTimerPolicy(Object status, boolean shareOn) {
        if (shareOn) XposedHelpers.callMethod(status, "F", !enabled);
    }

    private static void setShare(Context context, boolean desired) {
        Object status = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass(ModuleInfo.STATUS_CLASS, targetLoader), "v");
        XposedHelpers.callMethod(status, "G", context);
        XposedHelpers.callMethod(status, "F", desired);
        if (!Settings.System.putInt(context.getContentResolver(), "zte_share_enable", desired ? 1 : 0))
            throw new IllegalStateException("share setting write failed");
        Intent service = new Intent("com.zte.cn.zteshare.refresh_connective");
        service.setClassName(ModuleInfo.TARGET, ModuleInfo.TARGET + ".ConnectiveStateService");
        context.startService(service);
    }

    private static int shareState(Context context) {
        try { return Settings.System.getInt(context.getContentResolver(), "zte_share_enable", 0); }
        catch (Throwable e) { return -1; }
    }

    private static String targetVersion(Context context) {
        try { return context.getPackageManager().getPackageInfo(ModuleInfo.TARGET, 0).versionName; }
        catch (Throwable e) { return "读取失败"; }
    }

    private static String dexHash(String apk) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (ZipFile z = new ZipFile(apk)) {
            ZipEntry entry = z.getEntry("classes.dex");
            if (entry == null) throw new IllegalStateException("classes.dex missing");
            try (InputStream in = z.getInputStream(entry)) {
                byte[] buffer = new byte[32768];
                int n;
                while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return hex.toString();
    }

    private static void log(String text) { XposedBridge.log("[RedMagicShareKeepOn] " + text); }
}
