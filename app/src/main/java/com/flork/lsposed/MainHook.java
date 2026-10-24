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
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.alberun.mamoball")) return;

        XposedBridge.log("Flork Loader: Mamoball detectado! Preparando ambiente...");

        // 1. Roda comandos ROOT pra copiar os arquivos e dar permissão
        try {
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(su.getOutputStream());
            
            os.writeBytes("mkdir -p /data/data/com.alberun.mamoball/files\n");
            os.writeBytes("mkdir -p /data/local/tmp\n");
            
            // Copia pra pasta do jogo E pra pasta tmp (pra garantir)
            os.writeBytes("cp /sdcard/Download/adm/libc++_shared.so /data/data/com.alberun.mamoball/files/\n");
            os.writeBytes("cp /sdcard/Download/adm/libflork_android.so /data/data/com.alberun.mamoball/files/\n");
            os.writeBytes("cp /sdcard/Download/adm/classes9.dex /data/data/com.alberun.mamoball/files/\n");
            os.writeBytes("cp /sdcard/Download/adm/AndroidManifest.xml /data/data/com.alberun.mamoball/files/\n");
            
            os.writeBytes("cp /sdcard/Download/adm/libc++_shared.so /data/local/tmp/\n");
            os.writeBytes("cp /sdcard/Download/adm/libflork_android.so /data/local/tmp/\n");
            os.writeBytes("cp /sdcard/Download/adm/classes9.dex /data/local/tmp/\n");
            
            os.writeBytes("appops set com.alberun.mamoball SYSTEM_ALERT_WINDOW allow\n");
            
            os.writeBytes("chmod 777 /data/data/com.alberun.mamoball/files/*\n");
            os.writeBytes("chmod 777 /data/local/tmp/*.so /data/local/tmp/*.dex\n");
            os.flush();
            os.close();
            su.waitFor();
            
            XposedBridge.log("Flork Loader: Arquivos copiados e permissão aplicada!");
        } catch (Exception e) {
            XposedBridge.log("Flork Loader: Erro no root: " + e.getMessage());
        }

        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                if (activity == null) return;

                // 2. Carrega o DEX primeiro pra ter o ClassLoader
                try {
                    File dexFile = new File("/data/data/com.alberun.mamoball/files/classes9.dex");
                    byte[] dexBytes = new byte[(int) dexFile.length()];
                    FileInputStream fis = new FileInputStream(dexFile);
                    fis.read(dexBytes);
                    fis.close();

                    ByteBuffer bb = ByteBuffer.wrap(dexBytes);
                    ClassLoader cl = activity.getClassLoader();
                    InMemoryDexClassLoader dexLoader = new InMemoryDexClassLoader(bb, cl);

                    Class<?> florkClass = dexLoader.loadClass("com.flork.macro.FlorkOverlay");
                    XposedBridge.log("Flork Loader: Classe FlorkOverlay encontrada.");

                    // 3. A MÁGICA: Usa Runtime.load pra conectar a .so no mesmo ClassLoader do DEX
                    try {
                        Method runtimeLoad = Class.forName("java.lang.Runtime").getDeclaredMethod("load", String.class, ClassLoader.class);
                        runtimeLoad.setAccessible(true);
                        
                        // Carrega a libc++ no ClassLoader do botão
                        runtimeLoad.invoke(Runtime.getRuntime(), "/data/data/com.alberun.mamoball/files/libc++_shared.so", florkClass.getClassLoader());
                        // Carrega a libflork no ClassLoader do botão
                        runtimeLoad.invoke(Runtime.getRuntime(), "/data/data/com.alberun.mamoball/files/libflork_android.so", florkClass.getClassLoader());
                        
                        XposedBridge.log("Flork Loader: Bibliotecas linkadas ao ClassLoader do botão!");
                    } catch (Throwable t) {
                        XposedBridge.log("Flork Loader: Erro ao linkar libs: " + t.getMessage());
                        // Fallback se o Runtime.load falhar
                        System.load("/data/data/com.alberun.mamoball/files/libflork_android.so");
                    }

                    // 4. Chama o install() pra colar o botão na tela
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
