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

        XposedBridge.log("Flork Loader: Mamoball detectado! Iniciando root...");

        // 1. A MÁGICA ACONTECE AQUI: Substitui a libil2cpp.so via root ANTES do jogo carregar
        try {
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(su.getOutputStream());
            
            // Acha o caminho da libil2cpp.so do jogo
            os.writeBytes("LIB_PATH=$(find /data/app -name libil2cpp.so | head -n 1)\n");
            
            // Faz o bind mount da sua libil2cpp modificada (da pasta adm) por cima da original
            os.writeBytes("cp /sdcard/Download/adm/libil2cpp.so /data/local/tmp/libil2cpp_mod.so\n");
            os.writeBytes("chmod 644 /data/local/tmp/libil2cpp_mod.so\n");
            os.writeBytes("mount --bind /data/local/tmp/libil2cpp_mod.so $LIB_PATH\n");
            
            XposedBridge.log("Flork Loader: libil2cpp.so modificada montada com sucesso!");

            // Copia o resto dos arquivos pra pasta do jogo
            os.writeBytes("mkdir -p /data/data/com.alberun.mamoball/files\n");
            os.writeBytes("cp /sdcard/Download/adm/libc++_shared.so /data/data/com.alberun.mamoball/files/\n");
            os.writeBytes("cp /sdcard/Download/adm/libflork_android.so /data/data/com.alberun.mamoball/files/\n");
            os.writeBytes("cp /sdcard/Download/adm/classes9.dex /data/data/com.alberun.mamoball/files/\n");
            os.writeBytes("cp /sdcard/Download/adm/AndroidManifest.xml /data/data/com.alberun.mamoball/files/\n");
            
            // Permissão pro botão aparecer e permissão de root nos arquivos
            os.writeBytes("appops set com.alberun.mamoball SYSTEM_ALERT_WINDOW allow\n");
            os.writeBytes("chmod 777 /data/data/com.alberun.mamoball/files/*\n");
            os.flush();
            os.close();
            su.waitFor();
            
            XposedBridge.log("Flork Loader: Ambiente preparado!");
        } catch (Exception e) {
            XposedBridge.log("Flork Loader: Erro no root: " + e.getMessage());
        }

        // 2. Hooka a tela do jogo pra carregar as .so e o DEX e colar o botão
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                if (activity == null) return;

                // Carrega a libc++ e a libflork do cheat
                try {
                    System.load("/data/data/com.alberun.mamoball/files/libc++_shared.so");
                    System.load("/data/data/com.alberun.mamoball/files/libflork_android.so");
                    XposedBridge.log("Flork Loader: libflork_android.so carregada.");
                } catch (Throwable t) {
                    XposedBridge.log("Flork Loader: Erro nas libs: " + t.getMessage());
                }

                // Carrega o DEX do botão
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
