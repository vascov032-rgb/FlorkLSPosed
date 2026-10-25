package com.flork.lsposed;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import java.io.DataOutputStream;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {
    
    private static Button macroButton;
    private static boolean isHolding = false;
    private static Handler handler = new Handler(Looper.getMainLooper());
    private static DataOutputStream suOutput;
    private static boolean visible = true;

    private static final int KICK_X = 2047;
    private static final int KICK_Y = 585;
    private static final int POWER_X = 2383;
    private static final int POWER_Y = 948;

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.alberun.mamoball")) return;

        XposedBridge.log("Flork Loader: Mamoball detectado! Preparando...");

        // 1. Abre um shell SU persistente para não travar o celular
        try {
            Process suProcess = Runtime.getRuntime().exec("su");
            suOutput = new DataOutputStream(suProcess.getOutputStream());
        } catch (Exception e) {}

        // 2. Hook para capturar o botão criado pelo classes9.dex
        try {
            XposedHelpers.findAndHookMethod("android.view.WindowManagerImpl", lpparam.classLoader, "addView", View.class, ViewGroup.LayoutParams.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View v = (View) param.args[0];
                    if (v instanceof Button) {
                        Button b = (Button) v;
                        if (b.getText().toString().toUpperCase().contains("KICK")) {
                            XposedBridge.log("Flork Loader: Botao do classes9.dex capturado! Trocando evento...");
                            macroButton = b;
                            
                            // Trocamos o OnTouchListener original (que chamava nativeStartMacro) pelo nosso
                            b.setOnTouchListener(new View.OnTouchListener() {
                                @Override
                                public boolean onTouch(View v, MotionEvent event) {
                                    switch (event.getAction()) {
                                        case MotionEvent.ACTION_DOWN:
                                            isHolding = true;
                                            handler.post(holdRunnable);
                                            return true;
                                        case MotionEvent.ACTION_UP:
                                            handler.removeCallbacks(holdRunnable);
                                            if (isHolding) {
                                                isHolding = false;
                                            }
                                            return true;
                                    }
                                    return false;
                                }
                            });
                        }
                    }
                }
            });
        } catch (Throwable t) {}

        // 3. Hook nos toques na tela pra 4 dedos
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "dispatchTouchEvent", MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    MotionEvent event = (MotionEvent) param.args[0];
                    if (event != null && event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                        if (event.getPointerCount() == 4 && macroButton != null) {
                            visible = !visible;
                            macroButton.setAlpha(visible ? 1f : 0f);
                        }
                    }
                }
            });
        } catch (Throwable t) {}

        // 4. Hook no onCreate para carregar a .so e o classes9.dex
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final Activity activity = (Activity) param.thisObject;
                if (activity == null) return;

                activity.runOnUiThread(() -> {
                    try {
                        // Copia os arquivos da pasta adm
                        Process su = Runtime.getRuntime().exec("su");
                        DataOutputStream os = new DataOutputStream(su.getOutputStream());
                        os.writeBytes("mkdir -p /data/data/com.alberun.mamoball/files\n");
                        os.writeBytes("cp /sdcard/Download/adm/libc++_shared.so /data/data/com.alberun.mamoball/files/\n");
                        os.writeBytes("cp /sdcard/Download/adm/libflork_android.so /data/data/com.alberun.mamoball/files/\n");
                        os.writeBytes("cp /sdcard/Download/adm/classes9.dex /data/data/com.alberun.mamoball/files/\n");
                        os.writeBytes("appops set com.alberun.mamoball SYSTEM_ALERT_WINDOW allow\n");
                        os.writeBytes("chmod 777 /data/data/com.alberun.mamoball/files/*\n");
                        os.flush();
                        os.close();
                        su.waitFor();

                        // Carrega libs
                        System.load("/data/data/com.alberun.mamoball/files/libc++_shared.so");
                        System.load("/data/data/com.alberun.mamoball/files/libflork_android.so");

                        // Carrega o DEX
                        java.io.File dexFile = new java.io.File("/data/data/com.alberun.mamoball/files/classes9.dex");
                        byte[] dexBytes = new byte[(int) dexFile.length()];
                        java.io.FileInputStream fis = new java.io.FileInputStream(dexFile);
                        fis.read(dexBytes);
                        fis.close();

                        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(dexBytes);
                        ClassLoader cl = activity.getClassLoader();
                        dalvik.system.InMemoryDexClassLoader dexLoader = new dalvik.system.InMemoryDexClassLoader(bb, cl);

                        Class<?> florkClass = dexLoader.loadClass("com.flork.macro.FlorkOverlay");
                        
                        // Chama o install do classes9.dex pra criar o botão
                        java.lang.reflect.Method installMethod = florkClass.getMethod("install", Activity.class, String.class);
                        installMethod.invoke(null, activity, "FlorkMacroBridge");
                        
                        XposedBridge.log("Flork Loader: Botao do DEX criado.");
                    } catch (Throwable e) {
                        XposedBridge.log("Erro: " + e.getMessage());
                    }
                });
            }
        });
    }

    private static Runnable holdRunnable = new Runnable() {
        @Override
        public void run() {
            if (isHolding) {
                executeTap();
                handler.postDelayed(this, 1); // 1ms de delay
            }
        }
    };

    private static void executeTap() {
        new Thread(() -> {
            try {
                if (suOutput != null) {
                    suOutput.writeBytes("/system/bin/input tap " + KICK_X + " " + KICK_Y + "\n");
                    suOutput.writeBytes("/system/bin/input tap " + POWER_X + " " + POWER_Y + "\n");
                    suOutput.flush();
                }
            } catch (Exception e) {}
        }).start();
    }
}
