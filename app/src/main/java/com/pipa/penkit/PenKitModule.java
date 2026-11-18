package com.pipa.penkit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * system_server module:
 *  - intercepts the pen tail button (scan codes 188/189/190) and launches a configured target
 *  - optionally forces the pen channel open (replaces MaxMiPad's "remove bluetooth restriction")
 *  - optionally keeps Scribe enabled for third-party pens
 *  - optionally stops MIUI from consuming stylus key gestures
 */
public final class PenKitModule extends XposedModule {

    private static final String TAG = "PipaPenKit";
    private static final int KEY_F18 = 188;
    private static final int KEY_F19 = 189;
    private static final int KEY_F20 = 190;
    private static final String AUTHORITY = "com.pipa.penkit.config";
    private static final String ACTION_LOG = "com.pipa.penkit.LOG";
    private static final String PKG = "com.pipa.penkit";
    private static final String[] WINDOW_MANAGER_CLASSES = {
            "com.android.server.wm.PhoneWindowManager",
            "com.android.server.policy.PhoneWindowManager",
    };

    private static volatile Field sContextField;
    private static volatile Context sSystemContext;
    private static volatile boolean sPenEnable = true;
    private static volatile boolean sScribeEnable = true;
    private static volatile boolean sIgnoreKeyGesture = false;
    private static volatile int sDriverVersion = 2;
    private static volatile boolean sSlotSingle = true;
    private static volatile boolean sSlotDouble = true;
    private static volatile boolean sSlotLong = true;
    private static volatile boolean sNoMagicPointer = false;
    private static volatile boolean sRestoreEsc = false;
    private static volatile boolean sFingerNum4 = false;
    private static volatile boolean sDisableFixedOrientation = false;
    private static volatile boolean sFixGestureFilter = false;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        String process = param.getProcessName();
        log(Log.INFO, TAG, "loaded in " + process + " systemServer=" + param.isSystemServer());
        if (!param.isSystemServer() && "com.miui.home".equals(process)) {
            installHomeHooks(param.getClassLoader());
        }
    }

    /** Hooks that must live inside com.miui.home. */
    private void installHomeHooks(ClassLoader cl) {
        try {
            Class<?> c = Class.forName("com.miui.home.recents.GestureModeApp", false, cl);
            Method target = null;
            for (Method method : c.getDeclaredMethods()) {
                if ("isSupportGestureOperation".equals(method.getName())) {
                    target = method;
                    break;
                }
            }
            if (target == null) {
                log(Log.WARN, TAG, "GestureModeApp#isSupportGestureOperation missing");
                return;
            }
            hook(target).setId("penkit-gesture-filter")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (!sFixGestureFilter) {
                            return chain.proceed();
                        }
                        Object motion = null;
                        for (Object arg : chain.getArgs()) {
                            if (arg instanceof android.view.MotionEvent) {
                                motion = arg;
                                break;
                            }
                        }
                        if (motion == null) {
                            return chain.proceed();
                        }
                        android.view.MotionEvent event = (android.view.MotionEvent) motion;
                        boolean touch = event.getDevice() == null
                                || event.getDevice().supportsSource(
                                        android.view.InputDevice.SOURCE_TOUCHSCREEN);
                        if (!touch || event.getPointerCount() < 3) {
                            return chain.proceed();
                        }
                        return Boolean.valueOf(readThreeGestureSetting());
                    });
            log(Log.INFO, TAG, "hooked GestureModeApp#isSupportGestureOperation");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "no home gesture hook: " + t);
        }
    }

    private boolean readThreeGestureSetting() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getDeclaredMethod("currentActivityThread").invoke(null);
            android.content.Context ctx = null;
            if (thread != null) {
                ctx = (android.content.Context) at.getDeclaredMethod("getApplication")
                        .invoke(thread);
            }
            if (ctx == null) {
                return true;
            }
            String v = android.provider.Settings.System.getString(
                    ctx.getContentResolver(), "enable_three_gesture");
            return v != null && Integer.parseInt(v) > 0;
        } catch (Throwable t) {
            return true;
        }
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        ClassLoader cl = param.getClassLoader();
        installTailKeyHook(cl);
        installScribeHooks(cl);
        installKeyGestureHooks(cl);
        installPenListenerHook(cl);
        installSystemTweaks(cl);
        heartbeatLater(systemContext());
    }

    // ── tail key ───────────────────────────────────────────────

    private void installTailKeyHook(ClassLoader cl) {
        for (String className : WINDOW_MANAGER_CLASSES) {
            try {
                Class<?> cls = Class.forName(className, false, cl);
                Method method = findMethod(cls, "interceptKeyBeforeDispatching",
                        android.os.IBinder.class, KeyEvent.class, int.class);
                if (method == null) {
                    log(Log.WARN, TAG, className + " found but method missing");
                    continue;
                }
                hook(method)
                        .setId("penkit-quickkey")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            KeyEvent event = (KeyEvent) chain.getArg(1);
                            int code = event.getKeyCode();
                            int scan = event.getScanCode();
                            boolean isTailKey = scan == KEY_F20 || scan == KEY_F19 || scan == KEY_F18
                                    || code == KEY_F20 || code == KEY_F19 || code == KEY_F18;
                            if (!isTailKey) {
                                return chain.proceed();
                            }
                            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                                return chain.proceed();
                            }
                            Context ctx = resolveContext(chain.getThisObject());
                            if (ctx == null) {
                                log(Log.WARN, TAG, "tail key but context is null");
                                return chain.proceed();
                            }
                            int slot = (scan == KEY_F20 || code == KEY_F20) ? 1
                                    : (scan == KEY_F19 || code == KEY_F19) ? 2 : 3;
                            boolean enabled = slot == 1 ? sSlotSingle : slot == 2 ? sSlotDouble : sSlotLong;
                            if (!enabled) {
                                return chain.proceed();
                            }
                            if (launch(ctx, slot)) {
                                return 0L;
                            }
                            return chain.proceed();
                        });
                log(Log.INFO, TAG, "hooked " + className + "#" + method.getName());
                return;
            } catch (Throwable t) {
                log(Log.WARN, TAG, "cannot hook " + className + ": " + t);
            }
        }
    }

    // ── pen channel / scribe / key gestures ────────────────────

    private void installPenListenerHook(ClassLoader cl) {
        String[] names = {
                "com.miui.server.input.stylus.MiuiStylusDeviceListener",
                "com.miui.server.stylus.MiuiStylusDeviceListener",
        };
        for (String name : names) {
            try {
                Class<?> cls = Class.forName(name, false, cl);
                for (Method method : cls.getDeclaredMethods()) {
                    hook(method)
                            .setId("penkit-pen-listener-" + method.getName())
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                applyPenEnable();
                                return chain.proceed();
                            });
                }
                log(Log.INFO, TAG, "hooked " + name + " methods");
                return;
            } catch (Throwable t) {
                log(Log.WARN, TAG, "no pen listener hook on " + name + ": " + t);
            }
        }
    }

    private void installScribeHooks(ClassLoader cl) {
        try {
            Class<?> cls = Class.forName(
                    "com.android.server.inputmethod.StylusInputMethodSwitcher", false, cl);
            Method m = findMethod(cls, "isStylusDeviceConnected");
            if (m != null) {
                hook(m).setId("penkit-scribe-connected")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> sScribeEnable ? Boolean.TRUE : chain.proceed());
                log(Log.INFO, TAG, "hooked StylusInputMethodSwitcher#isStylusDeviceConnected");
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "no scribe switcher hook: " + t);
        }
        try {
            Class<?> inputDevice = Class.forName("android.view.InputDevice", false, cl);
            for (Method method : inputDevice.getDeclaredMethods()) {
                if (!"isXiaomiStylus".equals(method.getName())) {
                    continue;
                }
                hook(method).setId("penkit-is-xiaomi-stylus")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> sScribeEnable ? Integer.valueOf(2) : chain.proceed());
                log(Log.INFO, TAG, "hooked InputDevice#isXiaomiStylus");
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "no isXiaomiStylus hook: " + t);
        }
    }

    private void installKeyGestureHooks(ClassLoader cl) {
        String[] names = {
                "com.miui.server.input.stylus.MiuiStylusShortcutManager",
                "com.miui.server.input.stylus.MiuiStylusPageKeyListener",
                "com.miui.server.stylus.MiuiStylusPageKeyListener",
        };
        for (String name : names) {
            try {
                Class<?> cls = Class.forName(name, false, cl);
                int hooked = 0;
                for (Method method : cls.getDeclaredMethods()) {
                    String n = method.getName();
                    if (!"isPageKeyEnable".equals(n)
                            && !"needInterceptBeforeDispatching".equals(n)
                            && !"shouldInterceptKey".equals(n)) {
                        continue;
                    }
                    hook(method).setId("penkit-keygesture-" + n)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> sIgnoreKeyGesture ? Boolean.FALSE : chain.proceed());
                    hooked++;
                }
                log(Log.INFO, TAG, "hooked " + hooked + " key-gesture methods in " + name);
            } catch (Throwable t) {
                log(Log.WARN, TAG, "no key gesture hook on " + name + ": " + t);
            }
        }
    }

    private void installSystemTweaks(ClassLoader cl) {
        // 1. Magic Pointer off (class names differ between MIUI versions)
        String[] mpClasses = {
                "android.magicpointer.MiuiMagicPointerStub",
                "com.android.server.input.MiuiMagicPointerServiceStubHead",
                "android.magicpointer.util.MiuiMagicPointerUtils",
        };
        for (String mp : mpClasses) {
            try {
                Class<?> c = Class.forName(mp, false, cl);
                int count = 0;
                for (Method method : c.getDeclaredMethods()) {
                    String n = method.getName();
                    if (!"isEnable".equals(n) && !"isSupportMagicPointer".equals(n)) {
                        continue;
                    }
                    hook(method).setId("penkit-magic-pointer-" + n)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> sNoMagicPointer ? Boolean.FALSE : chain.proceed());
                    count++;
                }
                log(Log.INFO, TAG, "magic pointer " + mp + ": hooked " + count);
            } catch (Throwable t) {
                log(Log.WARN, TAG, "magic pointer class missing: " + mp);
            }
        }
        try {
            Class<?> c = Class.forName("com.android.server.SystemServerImpl", false, cl);
            Method m = findMethod(c, "addMagicPointerManagerService");
            if (m != null) {
                hook(m).setId("penkit-magic-pointer-service")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> sNoMagicPointer ? null : chain.proceed());
                log(Log.INFO, TAG, "hooked SystemServerImpl#addMagicPointerManagerService");
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "no magic pointer service: " + t);
        }

        // 2. ESC key restored (stop MIUI forcing it to BACK)
        try {
            Class<?> c = Class.forName("com.android.server.input.config.InputCommonConfig", false, cl);
            Method m = findMethod(c, "setPadMode", boolean.class);
            if (m != null) {
                hook(m).setId("penkit-restore-esc")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            if (sRestoreEsc) {
                                java.util.List<Object> args = chain.getArgs();
                                if (args != null && !args.isEmpty()) {
                                    args.set(0, Boolean.FALSE);
                                }
                            }
                            return chain.proceed();
                        });
                log(Log.INFO, TAG, "hooked InputCommonConfig#setPadMode");
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "no InputCommonConfig: " + t);
        }

        // 3. three-finger gestures need four fingers
        try {
            Class<?> c = Class.forName(
                    "com.miui.server.input.gesture.multifingergesture.gesture.BaseMiuiMultiFingerGesture",
                    false, cl);
            Method m = findMethod(c, "getFunctionNeedFingerNum");
            if (m != null) {
                hook(m).setId("penkit-finger-num")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> sFingerNum4 ? Integer.valueOf(4) : chain.proceed());
                log(Log.INFO, TAG, "hooked BaseMiuiMultiFingerGesture#getFunctionNeedFingerNum");
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "no multi finger gesture class: " + t);
        }

        // 4. disable fixed orientation for all apps
        try {
            Class<?> c = Class.forName(
                    "com.android.server.wm.MiuiFixedOrientationController", false, cl);
            Method m = findMethod(c, "shouldDisableFixedOrientation", String.class);
            if (m != null) {
                hook(m).setId("penkit-fixed-orientation")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> sDisableFixedOrientation ? Boolean.TRUE : chain.proceed());
                log(Log.INFO, TAG, "hooked MiuiFixedOrientationController#shouldDisableFixedOrientation");
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "no fixed orientation controller: " + t);
        }
    }

    private void applyPenEnable() {
        if (!sPenEnable) {
            return;
        }
        try {
            Class<?> cls = Class.forName("miui.util.ITouchFeature");
            Object instance = cls.getDeclaredMethod("getInstance").invoke(null);
            int flag = 0x10 | (sDriverVersion & 0x0F);
            cls.getDeclaredMethod("setTouchMode", int.class, int.class, int.class)
                    .invoke(instance, 0, 20, flag);
        } catch (Throwable t) {
            log(Log.WARN, TAG, "setTouchMode failed: " + t);
        }
    }

    // ── config / heartbeat ─────────────────────────────────────

    private void heartbeat(Context ctx) {
        heartbeatAttempt(ctx, 0);
    }

    private void heartbeatAttempt(Context ctx, int attempt) {
        if (ctx == null) {
            return;
        }
        Bundle result = callProvider(ctx, "heartbeat", null);
        if (result != null) {
            loadConfig(ctx);
            applyPenEnable();
            report(ctx, "hook 已安装");
            return;
        }
        if (attempt < 8) {
            try {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> heartbeatAttempt(ctx, attempt + 1), 15000L);
            } catch (Throwable ignored) {
            }
        }
    }

    private void heartbeatLater(Context ctx) {
        if (ctx == null) {
            return;
        }
        try {
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> heartbeat(ctx), 20000L);
        } catch (Throwable t) {
            log(Log.WARN, TAG, "cannot schedule heartbeat", t);
        }
    }

    private void loadConfig(Context ctx) {
        Bundle b = callProvider(ctx, "config", null);
        if (b == null) {
            return;
        }
        sPenEnable = b.getBoolean("pen_enable", true);
        sScribeEnable = b.getBoolean("scribe_enable", true);
        sIgnoreKeyGesture = b.getBoolean("ignore_key_gesture", false);
        sDriverVersion = b.getInt("driver_version", 2);
        sSlotSingle = b.getBoolean("slot_single", true);
        sSlotDouble = b.getBoolean("slot_double", true);
        sSlotLong = b.getBoolean("slot_long", true);
        sNoMagicPointer = b.getBoolean("no_magic_pointer", false);
        sRestoreEsc = b.getBoolean("restore_esc", false);
        sFingerNum4 = b.getBoolean("finger_num_4", false);
        sDisableFixedOrientation = b.getBoolean("disable_fixed_orientation", false);
        sFixGestureFilter = b.getBoolean("fix_gesture_filter", false);
        log(Log.INFO, TAG, "config pen=" + sPenEnable + " scribe=" + sScribeEnable
                + " ignoreGesture=" + sIgnoreKeyGesture + " ver=" + sDriverVersion);
    }

    // ── launch ─────────────────────────────────────────────────

    private boolean launch(Context ctx, int slot) {
        loadConfig(ctx);
        Bundle b = callProvider(ctx, "target", String.valueOf(slot));
        String target = b == null ? null : b.getString("target");
        if (target == null || target.trim().isEmpty()) {
            report(ctx, "slot " + slot + ": 未配置目标");
            return false;
        }
        target = target.trim();
        try {
            Intent intent;
            ComponentName component = ComponentName.unflattenFromString(target);
            if (component != null) {
                intent = new Intent().setComponent(component);
            } else {
                intent = ctx.getPackageManager().getLaunchIntentForPackage(target);
            }
            if (intent == null) {
                report(ctx, "slot " + slot + ": 找不到 " + target);
                return false;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            ctx.startActivity(intent);
            report(ctx, "slot " + slot + " -> " + target);
            return true;
        } catch (Throwable t) {
            report(ctx, "slot " + slot + " 启动失败: " + t);
            return false;
        }
    }

    private Bundle callProvider(Context ctx, String method, String arg) {
        try {
            return ctx.getContentResolver().call(
                    Uri.parse("content://" + AUTHORITY), method, arg, null);
        } catch (Throwable t) {
            log(Log.WARN, TAG, "provider call failed: " + method);
            return null;
        }
    }

    private void report(Context ctx, String msg) {
        log(Log.INFO, TAG, msg);
        try {
            Intent i = new Intent(ACTION_LOG);
            i.setPackage(PKG);
            i.putExtra("msg", msg);
            ctx.sendBroadcast(i);
        } catch (Throwable ignored) {
        }
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static Context systemContext() {
        if (sSystemContext != null) {
            return sSystemContext;
        }
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getDeclaredMethod("currentActivityThread").invoke(null);
            if (thread != null) {
                sSystemContext = (Context) at.getDeclaredMethod("getSystemContext").invoke(thread);
            }
        } catch (Throwable ignored) {
        }
        return sSystemContext;
    }

    private static Context resolveContext(Object windowManager) {
        Context ctx = null;
        if (windowManager != null) {
            try {
                Field field = sContextField;
                if (field == null) {
                    Class<?> c = windowManager.getClass();
                    while (c != null && field == null) {
                        try {
                            field = c.getDeclaredField("mContext");
                            field.setAccessible(true);
                        } catch (NoSuchFieldException ignored) {
                            c = c.getSuperclass();
                        }
                    }
                    sContextField = field;
                }
                if (field != null) {
                    ctx = (Context) field.get(windowManager);
                }
            } catch (Throwable ignored) {
            }
        }
        return ctx != null ? ctx : systemContext();
    }
}
