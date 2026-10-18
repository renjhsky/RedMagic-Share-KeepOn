#!/usr/bin/env python3
"""Exercise the real HookEntry against an in-memory Xposed test double, on a host JVM.

This verifies hook arguments, callback suppression, rollback, and fingerprint gating.
It is not a substitute for Android/LSPosed execution or the 15-minute phone test.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent
SOURCES = {
"android/app/Application.java": """package android.app;
public class Application { private void attach(android.content.Context c) {} }
""",
"android/content/pm/ApplicationInfo.java": """package android.content.pm;
public class ApplicationInfo { public String sourceDir; }
""",
"de/robv/android/xposed/IXposedHookLoadPackage.java": """package de.robv.android.xposed;
public interface IXposedHookLoadPackage {
 void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam p) throws Throwable;
}
""",
"de/robv/android/xposed/callbacks/XC_LoadPackage.java": """package de.robv.android.xposed.callbacks;
public class XC_LoadPackage {
 public static class LoadPackageParam {
  public String packageName, processName; public boolean isFirstApplication;
  public ClassLoader classLoader; public android.content.pm.ApplicationInfo appInfo;
 }
}
""",
"de/robv/android/xposed/XC_MethodHook.java": """package de.robv.android.xposed;
import java.lang.reflect.Member;
public class XC_MethodHook {
 protected void beforeHookedMethod(MethodHookParam p) throws Throwable {}
 protected void afterHookedMethod(MethodHookParam p) throws Throwable {}
 public static final class MethodHookParam {
  public Object[] args; public Object thisObject; public boolean returnEarly;
  private Object result; private Throwable throwable;
  public Object getResult() { return result; }
  public void setResult(Object r) { result=r; throwable=null; returnEarly=true; }
  public boolean hasThrowable() { return throwable!=null; }
  public Throwable getThrowable() { return throwable; }
  public void setThrowable(Throwable t) { throwable=t; returnEarly=true; }
 }
 public class Unhook {
  private final Member method;
  public Unhook(Member m) { method=m; }
  public void unhook() { XposedBridge.hooks.remove(method); }
 }
}
""",
"de/robv/android/xposed/XposedBridge.java": """package de.robv.android.xposed;
import java.lang.reflect.Member;
import java.util.LinkedHashMap;
public class XposedBridge {
 public static final LinkedHashMap<Member,XC_MethodHook> hooks=new LinkedHashMap<>();
 public static int attempts, failAt;
 public static XC_MethodHook.Unhook hookMethod(Member m, XC_MethodHook h) {
  attempts++; if (failAt!=0 && attempts==failAt) throw new IllegalStateException("test failure");
  hooks.put(m,h); return h.new Unhook(m);
 }
 public static void log(String s) {}
}
""",
"de/robv/android/xposed/XposedHelpers.java": """package de.robv.android.xposed;
import java.lang.reflect.Method;
public class XposedHelpers {
 public static Object callMethod(Object object, String name, Object... args) {
  try {
   for(Method m:object.getClass().getDeclaredMethods()) if(m.getName().equals(name) && m.getParameterCount()==args.length) {
    m.setAccessible(true); return m.invoke(object,args);
   }
   throw new NoSuchMethodException(name);
  } catch(Exception e) { throw new RuntimeException(e); }
 }
 public static Object callStaticMethod(Class<?> type, String name, Object... args) {
  try {
   for(Method m:type.getDeclaredMethods()) if(m.getName().equals(name) && m.getParameterCount()==args.length) {
    m.setAccessible(true); return m.invoke(null,args);
   }
   throw new NoSuchMethodException(name);
  } catch(Exception e) { throw new RuntimeException(e); }
 }
 public static Class<?> findClass(String n, ClassLoader l) {
  try { return Class.forName(n,false,l); } catch(Exception e) { throw new RuntimeException(e); }
 }
 public static XC_MethodHook.Unhook findAndHookMethod(String c, ClassLoader l, String n, Object... v) {
  return findAndHookMethod(findClass(c,l),n,v);
 }
 public static XC_MethodHook.Unhook findAndHookMethod(Class<?> c, String n, Object... v) {
  try {
   Class<?>[] args=new Class<?>[v.length-1];
   for(int i=0;i<args.length;i++) args[i]=(Class<?>)v[i];
   Method m=c.getDeclaredMethod(n,args);
   return XposedBridge.hookMethod(m,(XC_MethodHook)v[v.length-1]);
  } catch(Exception e) { throw new RuntimeException(e); }
 }
}
""",
"com/zte/cn/zteshare/ZteShareStatus.java": """package com.zte.cn.zteshare;
// Signature-only fixture. Original behavior is audited separately from the user's APK.
public class ZteShareStatus {
 public int timerArms, timerCancels;
 public void F(boolean v) {if(v)timerArms++;else timerCancels++;} public long z(android.content.Context c) {return 0;}
 public void q() {} public void t0(boolean v) {}
 public class b implements Runnable { public final ZteShareStatus a=ZteShareStatus.this; public void run(){} }
}
""",
"com/zte/cn/zteshare/activity/SettingsActivity.java": """package com.zte.cn.zteshare.activity;
public class SettingsActivity { public void onResume(){} }
""",
"de/robv/android/xposed/Harness.java": r"""package de.robv.android.xposed;
import io.github.redmagic.sharekeepon.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
public class Harness {
 static int checks;
 static void check(boolean ok,String what) { if(!ok) throw new AssertionError(what); checks++; }
 static Object value(String n) throws Exception { Field f=HookEntry.class.getDeclaredField(n); f.setAccessible(true); return f.get(null); }
 static void reset() throws Exception {
  for(String n:new String[]{"entered","summaryHook","receiverReady"}) {
   Field f=HookEntry.class.getDeclaredField(n); f.setAccessible(true); f.setBoolean(null,false);
  }
  for(String n:new String[]{"fingerprint","failure"}) {
   Field f=HookEntry.class.getDeclaredField(n); f.setAccessible(true); f.set(null,"");
  }
  Field f=HookEntry.class.getDeclaredField("coreHooks");f.setAccessible(true);f.setInt(null,0);
  Field enabled=HookEntry.class.getDeclaredField("enabled");enabled.setAccessible(true);enabled.setBoolean(null,true);
  for(String n:new String[]{"arms","restores","callbacks"}) ((AtomicInteger)value(n)).set(0);
  XposedBridge.hooks.clear();XposedBridge.attempts=0;XposedBridge.failAt=0;
 }
 static LoadPackageParam param(String apk) {
  LoadPackageParam p=new LoadPackageParam();p.packageName=ModuleInfo.TARGET;p.processName=ModuleInfo.TARGET;
  p.isFirstApplication=true;p.classLoader=Harness.class.getClassLoader();
  p.appInfo=new android.content.pm.ApplicationInfo();p.appInfo.sourceDir=apk;return p;
 }
 public static void main(String[] args) throws Throwable {
  HookEntry entry=new HookEntry();
  LoadPackageParam p=param(args[0]); p.packageName="com.android.systemui";entry.handleLoadPackage(p);
  check(XposedBridge.hooks.isEmpty(),"unrelated package untouched");
  p=param(args[0]);p.processName=ModuleInfo.TARGET+":other";entry.handleLoadPackage(p);
  check(XposedBridge.hooks.isEmpty(),"unrelated process untouched");
  p=param(args[0]);p.isFirstApplication=false;entry.handleLoadPackage(p);
  check(XposedBridge.hooks.isEmpty(),"secondary package load untouched");
  Path tampered=Files.createTempFile("changed-target-",".apk");
  try(ZipOutputStream z=new ZipOutputStream(Files.newOutputStream(tampered))) {
   z.putNextEntry(new ZipEntry("classes.dex"));z.write(new byte[]{1,2,3});z.closeEntry();
  }
  reset();entry.handleLoadPackage(param(tampered.toString()));
  check((Integer)value("coreHooks")==0,"changed DEX cannot install core hooks");
  check(!((String)value("failure")).isEmpty(),"changed DEX reports reason");
  check(XposedBridge.hooks.size()==1,"changed DEX installs diagnostic attach only");
  Files.delete(tampered);
  reset();entry.handleLoadPackage(param(args[0]));
  check((Integer)value("coreHooks")==3,"matching DEX installs all core hooks");
  check(XposedBridge.hooks.size()==5,"only three core hooks, settings summary and diagnostic attach");
  check((Boolean)value("summaryHook"),"summary hook installed");
  Class<?> status=Class.forName(ModuleInfo.STATUS_CLASS);
  Method arm=status.getDeclaredMethod("F",boolean.class);
  XC_MethodHook.MethodHookParam hp=new XC_MethodHook.MethodHookParam();hp.args=new Object[]{true};
  XposedBridge.hooks.get(arm).beforeHookedMethod(hp);
  check(Boolean.FALSE.equals(hp.args[0]),"arming converted to original cancellation branch");
  check(!hp.returnEarly,"original cancellation method still executes");
  check(((AtomicInteger)value("arms")).get()==1,"arming diagnostic incremented");
  hp=new XC_MethodHook.MethodHookParam();hp.args=new Object[]{false};
  XposedBridge.hooks.get(arm).beforeHookedMethod(hp);
  check(Boolean.FALSE.equals(hp.args[0]) && !hp.returnEarly,"manual off cancellation unchanged");
  check(((AtomicInteger)value("arms")).get()==1,"manual off not counted as arming");
  Method restore=status.getDeclaredMethod("z",android.content.Context.class);
  hp=new XC_MethodHook.MethodHookParam();hp.setResult(123L);
  XposedBridge.hooks.get(restore).afterHookedMethod(hp);
  check(Long.valueOf(0).equals(hp.getResult()),"persisted timer start suppressed");
  check(((AtomicInteger)value("restores")).get()==1,"old timer read counted");
  hp=new XC_MethodHook.MethodHookParam();Throwable error=new IllegalStateException("original");hp.setThrowable(error);
  XposedBridge.hooks.get(restore).afterHookedMethod(hp);
  check(hp.getThrowable()==error,"original preference error not concealed");
  Method callback=Class.forName(ModuleInfo.CALLBACK_CLASS).getDeclaredMethod("run");
  hp=new XC_MethodHook.MethodHookParam();XposedBridge.hooks.get(callback).beforeHookedMethod(hp);
  check(hp.returnEarly && hp.getResult()==null,"dedicated expiry callback prevented");
  check(!XposedBridge.hooks.containsKey(status.getDeclaredMethod("q")),"manual deferred cleanup not hooked");
  check(!XposedBridge.hooks.containsKey(status.getDeclaredMethod("t0",boolean.class)),"manual wait flag not hooked");
  Field enabled=HookEntry.class.getDeclaredField("enabled");enabled.setAccessible(true);enabled.setBoolean(null,false);
  hp=new XC_MethodHook.MethodHookParam();hp.args=new Object[]{true};
  XposedBridge.hooks.get(arm).beforeHookedMethod(hp);
  check(Boolean.TRUE.equals(hp.args[0]) && !hp.returnEarly,"disabled module allows original timer scheduling");
  hp=new XC_MethodHook.MethodHookParam();hp.setResult(123L);
  XposedBridge.hooks.get(restore).afterHookedMethod(hp);
  check(Long.valueOf(123L).equals(hp.getResult()),"disabled module allows old timer restore");
  hp=new XC_MethodHook.MethodHookParam();XposedBridge.hooks.get(callback).beforeHookedMethod(hp);
  check(!hp.returnEarly,"disabled module allows expiry callback");
  Object object=status.newInstance();
  Method policy=HookEntry.class.getDeclaredMethod("applyTimerPolicy",Object.class,boolean.class);policy.setAccessible(true);
  policy.invoke(null,object,true);
  check(status.getField("timerArms").getInt(object)==1,"turning module off rearms original timer while share is on");
  enabled.setBoolean(null,true);policy.invoke(null,object,true);
  check(status.getField("timerCancels").getInt(object)==1,"turning module on cancels existing timer");
  policy.invoke(null,object,false);
  check(status.getField("timerArms").getInt(object)==1 && status.getField("timerCancels").getInt(object)==1,"policy does not enable or time a disabled share switch");
  int before=XposedBridge.hooks.size();entry.handleLoadPackage(param(args[0]));
  check(XposedBridge.hooks.size()==before,"duplicate load does not duplicate hooks");
  reset();XposedBridge.failAt=3;
  Method install=HookEntry.class.getDeclaredMethod("installCore",ClassLoader.class);install.setAccessible(true);
  boolean failed=false;
  try { install.invoke(null,Harness.class.getClassLoader()); } catch(InvocationTargetException e) {failed=true;}
  check(failed && XposedBridge.hooks.isEmpty() && (Integer)value("coreHooks")==0,"partial install rolled back");
  System.out.println("PASS: "+checks+" host-JVM hook assertions (not an Android device test)");
 }
}
""",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tools", type=Path, required=True)
    parser.add_argument("--apk", type=Path, required=True, help="The original user-provided target APK")
    args = parser.parse_args()
    tools = args.tools.resolve()
    android = tools / "platform/android-35/android.jar"
    with tempfile.TemporaryDirectory(prefix="redmagic-hook-test-") as temp:
        root = Path(temp)
        for name, data in SOURCES.items():
            dest = root / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(data)
        classes = root / "classes"
        classes.mkdir()
        src = ROOT / "src/io/github/redmagic/sharekeepon"
        subprocess.run(["java", "-jar", str(tools / "ecj.jar"), "-source", "1.8", "-target", "1.8",
                        "-encoding", "UTF-8", "-classpath", str(android), "-d", str(classes),
                        *[str(x) for x in root.rglob("*.java")], str(src / "ModuleInfo.java"),
                        str(src / "HookEntry.java"), str(src / "ModuleConfig.java")], check=True)
        subprocess.run(["java", "-cp", str(classes) + ":" + str(android),
                        "de.robv.android.xposed.Harness", str(args.apk.resolve())], check=True)


if __name__ == "__main__":
    main()
