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

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, TAG, "loaded in " + param.getProcessName()
                + " systemServer=" + param.isSystemServer());
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        ClassLoader cl = param.getClassLoader();
        installTailKeyHook(cl);
        installScribeHooks(cl);
        installKeyGestureHooks(cl);
        installPenListenerHook(cl);
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
