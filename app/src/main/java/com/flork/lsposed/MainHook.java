package com.flork.lsposed;

import android.app.Activity;
import android.os.Bundle;
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
    
    // Coordenadas exatas que você passou
    private static final int KICK_X = 2047;
    private static final int KICK_Y = 585;
    private static final int POWER_X = 2383;
    private static final int POWER_Y = 948;

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.alberun.mamoball")) return;

        XposedBridge.log("Flork Loader: Mamoball detectado! Copiando arquivos...");

        // 1. Copia os arquivos originais da pasta adm pra pasta do jogo
        try {
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(su.getOutputStream());
            os.writeBytes("mkdir -p /data/data/com.alberun.mamoball/files\n");
            os.writeBytes("cp /sdcard/Download/adm/libc++_shared.so /data/data/com.alberun.mamoball/files/\n");
            os.writeBytes("cp /sdcard/Download/adm/libflork_android.so /data/data/com.alberun.mamoball/files/\n");
            os.writeBytes("cp /sdcard/Download/adm/classes9.dex /data/data/com.alberun.mamoball/files/\n");
            os.writeBytes("cp /sdcard/Download/adm/AndroidManifest.xml /data/data/com.alberun.mamoball/files/\n");
            os.writeBytes("appops set com.alberun.mamoball SYSTEM_ALERT_WINDOW allow\n");
            os.writeBytes("chmod 777 /data/data/com.alberun.mamoball/files/*\n");
            os.flush();
            os.close();
            su.waitFor();
            XposedBridge.log("Flork Loader: Arquivos copiados e permissão dada!");
        } catch (Exception e) {
            XposedBridge.log("Flork Loader: Erro no root: " + e.getMessage());
        }

        // 2. Hook na tela do jogo pra carregar as libs e o DEX
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                if (activity == null) return;

                try {
                    // Carrega as bibliotecas nativas
                    System.load("/data/data/com.alberun.mamoball/files/libc++_shared.so");
                    System.load("/data/data/com.alberun.mamoball/files/libflork_android.so");
                    XposedBridge.log("Flork Loader: Bibliotecas carregadas.");

                    // Carrega o seu DEX
                    File dexFile = new File("/data/data/com.alberun.mamoball/files/classes9.dex");
                    byte[] dexBytes = new byte[(int) dexFile.length()];
                    FileInputStream fis = new FileInputStream(dexFile);
                    fis.read(dexBytes);
                    fis.close();

                    ByteBuffer bb = ByteBuffer.wrap(dexBytes);
                    ClassLoader cl = activity.getClassLoader();
                    InMemoryDexClassLoader dexLoader = new InMemoryDexClassLoader(bb, cl);

                    Class<?> florkClass = dexLoader.loadClass("com.flork.macro.FlorkOverlay");

                    // 3. A MÁGICA: Intercepta o nativeStartMacro do seu DEX
                    try {
                        XposedHelpers.findAndHookMethod(florkClass, "nativeStartMacro", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                XposedBridge.log("Flork Loader: Botao clicado! Disparando input tap...");
                                new Thread(() -> {
                                    try {
                                        Process su = Runtime.getRuntime().exec("su");
                                        DataOutputStream os = new DataOutputStream(su.getOutputStream());
                                        // Dispara o Kick e o Power Kick
                                        os.writeBytes("input tap " + KICK_X + " " + KICK_Y + "\n");
                                        os.writeBytes("input tap " + POWER_X + " " + POWER_Y + "\n");
                                        os.flush();
                                        os.close();
                                        su.waitFor();
                                    } catch (Exception e) {}
                                }).start();
                            }
                        });
                        XposedBridge.log("Flork Loader: nativeStartMacro hookada com sucesso!");
                    } catch (Throwable t) {
                        XposedBridge.log("Flork Loader: Erro ao hookar nativeStartMacro: " + t.getMessage());
                    }

                    // 4. Chama o install() do seu DEX pra criar o botão na tela
                    Method installMethod = florkClass.getMethod("install", Activity.class, String.class);
                    installMethod.invoke(null, activity, "FlorkMacroBridge");
                    XposedBridge.log("Flork Loader: Botao injetado com SUCESSO!");

                } catch (Throwable t) {
                    XposedBridge.log("Flork Loader: Erro DEX: " + t.getMessage());
                }
            }
        });
    }
}
