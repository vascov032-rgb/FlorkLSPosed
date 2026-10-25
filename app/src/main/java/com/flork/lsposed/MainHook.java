package com.flork.lsposed;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import dalvik.system.InMemoryDexClassLoader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {
    
    private static boolean isHolding = false;
    private static Handler handler = new Handler(Looper.getMainLooper());
    private static DataOutputStream suOutput;

    private static final int KICK_X = 2047;
    private static final int KICK_Y = 585;
    private static final int POWER_X = 2383;
    private static final int POWER_Y = 948;

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.alberun.mamoball")) return;

        XposedBridge.log("Flork Loader: Mamoball detectado! Iniciando...");

        // 1. Abre o shell SU persistente
        try {
            Process suProcess = Runtime.getRuntime().exec("su");
            suOutput = new DataOutputStream(suProcess.getOutputStream());
        } catch (Exception e) {}

        // 2. A MÁGICA DEFINITIVA: Hook no botão assim que o seu DEX tentar colocar o clique nele
        try {
            XposedHelpers.findAndHookMethod(Button.class, "setOnTouchListener", View.OnTouchListener.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // Substitui o evento de clique original do classes9.dex pelo nosso
                    param.args[0] = new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            switch (event.getAction()) {
                                case MotionEvent.ACTION_DOWN:
                                    isHolding = true;
                                    handler.post(holdRunnable);
                                    // Retorna false para que o DEX original ainda consiga arrastar o botão
                                    return false; 
                                case MotionEvent.ACTION_UP:
                                case MotionEvent.ACTION_CANCEL:
                                    handler.removeCallbacks(holdRunnable);
                                    if (isHolding) {
                                        isHolding = false;
                                    }
                                    return false;
                            }
                            return false;
                        }
                    };
                }
            });
            XposedBridge.log("Flork Loader: Hook do clique ativado!");
        } catch (Throwable t) {
            XposedBridge.log("Erro no hook do clique: " + t.getMessage());
        }

        // 3. Hook no onCreate para carregar as libs e o DEX
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final Activity activity = (Activity) param.thisObject;
                if (activity == null) return;

                activity.runOnUiThread(() -> {
                    try {
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

                        System.load("/data/data/com.alberun.mamoball/files/libc++_shared.so");
                        System.load("/data/data/com.alberun.mamoball/files/libflork_android.so");

                        File dexFile = new File("/data/data/com.alberun.mamoball/files/classes9.dex");
                        byte[] dexBytes = new byte[(int) dexFile.length()];
                        FileInputStream fis = new FileInputStream(dexFile);
                        fis.read(dexBytes);
                        fis.close();

                        ByteBuffer bb = ByteBuffer.wrap(dexBytes);
                        ClassLoader cl = activity.getClassLoader();
                        InMemoryDexClassLoader dexLoader = new InMemoryDexClassLoader(bb, cl);

                        Class<?> florkClass = dexLoader.loadClass("com.flork.macro.FlorkOverlay");
                        
                        Method installMethod = florkClass.getMethod("install", Activity.class, String.class);
                        installMethod.invoke(null, activity, "FlorkMacroBridge");
                        
                        XposedBridge.log("Flork Loader: Botao injetado com SUCESSO!");
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
                handler.postDelayed(this, 1); // 1ms de loop
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
