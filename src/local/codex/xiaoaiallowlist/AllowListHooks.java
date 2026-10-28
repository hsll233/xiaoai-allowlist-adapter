package local.codex.xiaoaiallowlist;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class AllowListHooks implements IXposedHookLoadPackage {
  private static final String TAG = "XiaoAiAllowList";
  private static Context context;
  private static ClassLoader loader;
  private static boolean installed;
  private static volatile Set<String> launchable = new HashSet<String>();
  private static long catalogTime, settingsTime;
  private static boolean setting;
  private static int windows, expansions, callbacks;
  private static final ThreadLocal<WindowScope> scope = new ThreadLocal<WindowScope>();
  private static final class WindowScope {
    final Set<String> intercepted = new HashSet<String>();
    final Set<String> seen = new HashSet<String>();
    final boolean currentVisible, originalWhiteVisible, selfCheck;
    WindowScope(boolean currentVisible, boolean originalWhiteVisible) {
      this(currentVisible, originalWhiteVisible, false);
    }
    WindowScope(boolean currentVisible, boolean originalWhiteVisible, boolean selfCheck) {
      this.currentVisible = currentVisible; this.originalWhiteVisible = originalWhiteVisible;
      this.selfCheck = selfCheck;
    }
  }
  private static void log(String event) { Log.i(TAG, "{\"event\":\"" + event + "\"}"); }
  private static Object call(Object object, String method) {
    try { return XposedHelpers.callMethod(object, method); } catch (Throwable ignored) { return null; }
  }
  private static Object state() {
    return XposedHelpers.getStaticObjectField(XposedHelpers.findClass("yy.e", loader), "a");
  }
  private static boolean activeTask() {
    Object manager=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("yy.j",loader),"a");
    Object current=call(manager,"getCurrentState");
    Object states=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("gz.a",loader),"a");
    Object stop=call(states,"getSTATE_STOP"),init=call(states,"getSTATE_INIT");
    if(current==null||stop==null||init==null)return false;
    Object comparison=XposedHelpers.callMethod(current,"compareTo",stop);
    Object lower=XposedHelpers.callMethod(current,"compareTo",init);
    return comparison instanceof Number&&lower instanceof Number&&((Number)lower).intValue()>=0&&((Number)comparison).intValue()<0;
  }
  private static synchronized boolean enabled() {
    long now = SystemClock.elapsedRealtime();
    if (settingsTime != 0 && now - settingsTime < 1000L) return setting;
    try {
      Bundle settings = context.getContentResolver().call(SettingsProvider.URI, "get", null, null);
      setting = settings != null && settings.getBoolean("enabled", false);
    } catch (Throwable ignored) { setting = false; }
    settingsTime = now; return setting;
  }
  private static synchronized Set<String> launcherPackages() {
    long now = SystemClock.elapsedRealtime();
    if (catalogTime != 0 && now - catalogTime < 60000L) return launchable;
    Set<String> fresh = new HashSet<String>();
    try {
      fresh.addAll(AppAccess.enabledPackages(context));
      launchable = fresh; catalogTime = now;
    } catch (Throwable ignored) { log("launcherCatalogUnavailable"); }
    return launchable;
  }
  private static void expanded(WindowScope window, String pkg) {
    if (window.selfCheck) return;
    if (window.seen.add(pkg)) {
      int number = ++expansions;
      if (number <= 24 || number % 128 == 0) Log.i(TAG,
          "{\"event\":\"expandedWindowMembership\",\"count\":" + number
          + ",\"originalCurrentVisible\":" + window.currentVisible
          + ",\"originalWhiteVisible\":" + window.originalWhiteVisible + "}");
    }
  }
  @SuppressWarnings("unchecked") private static void readOnlySelfCheck() {
    WindowScope previous = scope.get();
    scope.remove();
    try {
      if (!enabled()) { log("selfCheckSkippedDisabled"); return; }
      Object states=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("gz.a",loader),"a");
      Object stop=call(states,"getSTATE_STOP"),init=call(states,"getSTATE_INIT"),complete=call(states,"getSTATE_COMPLETE");
      boolean initActive=((Number)XposedHelpers.callMethod(init,"compareTo",stop)).intValue()<0;
      boolean stopInactive=((Number)XposedHelpers.callMethod(stop,"compareTo",stop)).intValue()>=0;
      boolean completeInactive=((Number)XposedHelpers.callMethod(complete,"compareTo",stop)).intValue()>=0;
      Object unknown=call(states,"getSTATE_UNKNOWN");
      boolean unknownInactive=((Number)XposedHelpers.callMethod(unknown,"compareTo",init)).intValue()<0;
      Log.i(TAG,"{\"event\":\"sdkActiveGateSelfCheck\",\"initActive\":"+initActive+",\"stopInactive\":"+stopInactive+",\"completeInactive\":"+completeInactive+",\"unknownInactive\":"+unknownInactive+",\"currentActive\":"+activeTask()+"}");
      Object state = state();
      Object original = call(state, "getWhiteAppList");
      if (!(original instanceof CopyOnWriteArrayList)) throw new IllegalStateException("Unexpected SDK list type");
      Collection<String> source = (Collection<String>)original;
      CopyOnWriteArrayList<String> saved = new CopyOnWriteArrayList<String>(source);
      String probe = null;
      for (String pkg : launcherPackages()) if (!source.contains(pkg)) { probe = pkg; break; }
      if (probe == null) { log("selfCheckNoAdditionalCandidate"); return; }
      Object managerCurrent = call(XposedHelpers.getStaticObjectField(XposedHelpers.findClass("yy.g", loader), "r"), "getValue");
      WindowScope check = new WindowScope(false, false, true);
      scope.set(check);
      Object wrapped = call(state, "getWhiteAppList");
      if (!(wrapped instanceof Collection)) throw new IllegalStateException("SDK hook returned no collection");
      boolean added = ((Collection<?>)wrapped).contains(probe);
      boolean unknownRejected = !((Collection<?>)wrapped).contains("local.codex.nonexistent.selfcheck");
      check.intercepted.add(probe);
      boolean interceptedPreserved = !((Collection<?>)wrapped).contains(probe);
      scope.remove();
      Object after = call(state, "getWhiteAppList");
      boolean unchanged = after == original && saved.equals(source);
      boolean hookApplied = wrapped instanceof MembershipList;
      Log.i(TAG, "{\"event\":\"readOnlySelfCheck\",\"getterHookApplied\":" + hookApplied
          + ",\"originalContains\":false,\"expandedContains\":" + added
          + ",\"unknownRejected\":" + unknownRejected + ",\"explicitInterceptPreserved\":" + interceptedPreserved
          + ",\"sourceListUnchanged\":" + unchanged + ",\"managerFieldResolved\":" + (managerCurrent instanceof String) + "}");
    } catch (Throwable error) {
      Log.e(TAG, "{\"event\":\"selfCheckError\",\"error\":\"readOnlySelfCheck\",\"type\":\"" + error.getClass().getSimpleName() + "\"}");
    } finally {
      if (previous == null) scope.remove(); else scope.set(previous);
    }
  }
  private static void install() {
    final Class<?> stateClass = XposedHelpers.findClass("yy.e", loader);
    final Class<?> managerClass = XposedHelpers.findClass("yy.g", loader);
    XposedHelpers.findAndHookMethod(managerClass, "onWindowInfosChanged", List.class, new XC_MethodHook() {
      @Override protected void beforeHookedMethod(MethodHookParam p) {
        p.setObjectExtra("previousAllowlistScope", scope.get()); scope.remove();
        try {
          Object state = state();
          if (!enabled()) return;
          boolean active=activeTask();
          int seen=++callbacks;
          if(seen<=3||seen%128==0)Log.i(TAG,"{\"event\":\"windowCallbackObserved\",\"activeTask\":"+active+",\"interruptStatus\":"+Boolean.TRUE.equals(call(state,"getInterruptStatus"))+"}");
          if(!active)return;
          Object current = call(XposedHelpers.getStaticObjectField(managerClass, "r"), "getValue");
          Object original = call(state, "getWhiteAppList");
          boolean currentVisible = false, whiteVisible = false;
          if (p.args[0] instanceof List) for (Object window : (List<?>)p.args[0]) {
            Object pkg = call(window, "getPackageName");
            if (pkg != null && pkg.equals(current)) currentVisible = true;
            if (original instanceof Collection && ((Collection<?>)original).contains(pkg)) whiteVisible = true;
          }
          WindowScope local = new WindowScope(currentVisible, whiteVisible);
          Object intercepted = call(state, "getInterceptWindowList");
          if (intercepted instanceof List) for (Object item : (List<?>)intercepted) {
            if (item instanceof String) {
              String entry = (String)item; int separator = entry.indexOf("&&");
              if (separator > 0) local.intercepted.add(entry.substring(0, separator));
            }
          }
          scope.set(local);
          if (++windows <= 3) Log.i(TAG, "{\"event\":\"activeWindowCheck\",\"originalCurrentVisible\":"
              + currentVisible + ",\"originalWhiteVisible\":" + whiteVisible + "}");
        } catch (Throwable error) { log("windowScopeUnavailable"); }
      }
      @Override protected void afterHookedMethod(MethodHookParam p) {
        WindowScope previous = (WindowScope)p.getObjectExtra("previousAllowlistScope");
        if (previous == null) scope.remove(); else scope.set(previous);
      }
    });
    XposedHelpers.findAndHookMethod(stateClass, "getWhiteAppList", new XC_MethodHook() {
      @Override @SuppressWarnings("unchecked") protected void afterHookedMethod(MethodHookParam p) {
        WindowScope local = scope.get();
        if (local != null && !p.hasThrowable() && p.getResult() instanceof CopyOnWriteArrayList)
          p.setResult(new MembershipList((Collection<String>)p.getResult(),
              pkg -> launcherPackages().contains(pkg) && !local.intercepted.contains(pkg),
              pkg -> expanded(local, pkg)));
      }
    });
    // This switch is already true in this build's initializer. Observe it; do not present it as a new app unlock.
    XposedHelpers.findAndHookMethod("yy.d", loader, "getDisableWhiteListCheck", new XC_MethodHook() {
      private boolean sampled;
      @Override protected void afterHookedMethod(MethodHookParam p) {
        if (!sampled && !p.hasThrowable()) {
          sampled = true;
          Log.i(TAG, "{\"event\":\"instructionInterruptConfigObserved\",\"alreadyDisabled\":" + Boolean.TRUE.equals(p.getResult()) + "}");
        }
      }
    });
    log("hooksInstalled");
  }
  @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
    if (!"com.miui.voiceassist".equals(lp.packageName) || !"com.miui.voiceassist".equals(lp.processName)) return;
    XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
      @Override protected void afterHookedMethod(MethodHookParam p) {
        if (installed) return;
        try {
          context = (Context)p.args[0]; loader = lp.classLoader;
          if (!HostCompatibility.allow(context,loader)) {
            log("versionMismatchDisabled"); return;
          }
          install(); installed = true; log("loadedClientAllowlist");HostCompatibility.component(context,"window",true);
          new Handler(Looper.getMainLooper()).postDelayed(AllowListHooks::readOnlySelfCheck, 1500L);
        } catch (Throwable error) {
          HostCompatibility.component(context,"window",false);
          Log.e(TAG, "{\"event\":\"initError\",\"type\":\"" + error.getClass().getSimpleName() + "\"}");
        }
      }
    });
  }
}
