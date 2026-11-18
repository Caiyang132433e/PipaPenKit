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
 * system_server side: intercepts the pen's Bluetooth tail button
 * (Meta+F20 / F19 / F18) before dispatch and launches the configured target.
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

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, TAG, "loaded in " + param.getProcessName());
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        for (String className : WINDOW_MANAGER_CLASSES) {
            try {
                Class<?> cls = Class.forName(className, false, param.getClassLoader());
                Method method = cls.getDeclaredMethod(
                        "interceptKeyBeforeDispatching",
                        android.os.IBinder.class,
                        KeyEvent.class,
                        int.class);
                hook(method)
                        .setId("penkit-quickkey")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            KeyEvent event = (KeyEvent) chain.getArg(1);
                            int code = event.getKeyCode();
                            boolean isTailKey = code == KEY_F20 || code == KEY_F19 || code == KEY_F18;
                            if (!isTailKey || event.getAction() != KeyEvent.ACTION_DOWN) {
                                return chain.proceed();
                            }
                            Context ctx = resolveContext(chain.getThisObject());
                            if (ctx == null) {
                                return chain.proceed();
                            }
                            if (!isEnabled(ctx)) {
                                return chain.proceed();
                            }
                            int slot = code == KEY_F20 ? 1 : code == KEY_F19 ? 2 : 3;
                            launch(ctx, slot);
                            return 0L;
                        });
                log(Log.INFO, TAG, "hooked " + className);
                return;
            } catch (Throwable t) {
                log(Log.WARN, TAG, "cannot hook " + className + ": " + t);
            }
        }
        log(Log.ERROR, TAG, "no PhoneWindowManager hook installed");
    }

    private void launch(Context ctx, int slot) {
        String target = readString(ctx, "target", String.valueOf(slot));
        if (target == null || target.trim().isEmpty()) {
            report(ctx, "slot " + slot + ": 未配置目标");
            return;
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
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            ctx.startActivity(intent);
            report(ctx, "slot " + slot + " -> " + target);
        } catch (Throwable t) {
            report(ctx, "slot " + slot + " 启动失败: " + t);
        }
    }

    private boolean isEnabled(Context ctx) {
        try {
            Bundle b = ctx.getContentResolver().call(
                    Uri.parse("content://" + AUTHORITY), "enabled", null, null);
            return b == null || b.getBoolean("enabled", true);
        } catch (Throwable t) {
            return true;
        }
    }

    private String readString(Context ctx, String method, String arg) {
        try {
            Bundle b = ctx.getContentResolver().call(
                    Uri.parse("content://" + AUTHORITY), method, arg, null);
            return b == null ? null : b.getString("target");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "provider call failed", t);
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

    private static Context resolveContext(Object windowManager) {
        if (windowManager == null) {
            return null;
        }
        try {
            Field field = sContextField;
            if (field == null) {
                field = windowManager.getClass().getDeclaredField("mContext");
                field.setAccessible(true);
                sContextField = field;
            }
            return (Context) field.get(windowManager);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
