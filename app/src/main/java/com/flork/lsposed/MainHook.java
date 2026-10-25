package com.flork.lsposed;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
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
    private static Process suProcess;
    private static DataOutputStream suOutput;
    private static boolean visible = true;

    // SUAS COORDENADAS EXATAS
    private static final int KICK_X = 2047;
    private static final int KICK_Y = 585;
    private static final int POWER_X = 2383;
    private static final int POWER_Y = 948;

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.alberun.mamoball")) return;

        XposedBridge.log("Flork LSPosed: Mamoball detectado! Injetando Botao...");

        // 1. Abre um shell SU persistente (evita lag e fecha-lo antes do tempo)
        try {
            suProcess = Runtime.getRuntime().exec("su");
            suOutput = new DataOutputStream(suProcess.getOutputStream());
        } catch (Exception e) {
            XposedBridge.log("Flork LSPosed: Erro ao abrir SU: " + e.getMessage());
        }

        // 2. Intercepta os toques na tela pra ver se tem 4 dedos
        XposedHelpers.findAndHookMethod(Activity.class, "dispatchTouchEvent", MotionEvent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                MotionEvent event = (MotionEvent) param.args[0];
                if (event != null && event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                    if (event.getPointerCount() == 4) {
                        XposedBridge.log("Flork LSPosed: 4 dedos detectados!");
                        visible = !visible;
                        if (macroButton != null) {
                            ((Activity) param.thisObject).runOnUiThread(() -> {
                                macroButton.setAlpha(visible ? 1f : 0f);
                            });
                        }
                    }
                }
            }
        });

        // 3. Cria o botão na tela do jogo
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final Activity activity = (Activity) param.thisObject;
                if (activity == null || macroButton != null) return;

                activity.runOnUiThread(() -> {
                    try {
                        WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
                        macroButton = new Button(activity);
                        macroButton.setText("KICK+POWER");
                        macroButton.setTextColor(Color.WHITE);
                        macroButton.setTextSize(10);

                        GradientDrawable bg = new GradientDrawable();
                        bg.setShape(GradientDrawable.OVAL);
                        bg.setColor(Color.parseColor("#8000FF00"));
                        bg.setStroke(3, Color.GREEN);
                        macroButton.setBackground(bg);

                        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                                250, 250,
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                                PixelFormat.TRANSLUCENT);

                        params.gravity = Gravity.TOP | Gravity.LEFT;
                        params.x = 200;
                        params.y = 400;

                        macroButton.setOnTouchListener(new View.OnTouchListener() {
                            float initX, initY;
                            float initTouchX, initTouchY;
                            boolean isDragging = false;
                            @Override
                            public boolean onTouch(View v, MotionEvent event) {
                                switch (event.getAction()) {
                                    case MotionEvent.ACTION_DOWN:
                                        initX = params.x; initY = params.y;
                                        initTouchX = event.getRawX(); initTouchY = event.getRawY();
                                        isHolding = true;
                                        isDragging = false;
                                        handler.post(holdRunnable);
                                        return true;
                                    case MotionEvent.ACTION_MOVE:
                                        float dx = event.getRawX() - initTouchX;
                                        float dy = event.getRawY() - initTouchY;
                                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                            isDragging = true;
                                            isHolding = false;
                                            handler.removeCallbacks(holdRunnable);
                                        }
                                        params.x = (int) (initX + dx);
                                        params.y = (int) (initY + dy);
                                        wm.updateViewLayout(macroButton, params);
                                        return true;
                                    case MotionEvent.ACTION_UP:
                                        handler.removeCallbacks(holdRunnable);
                                        if (isHolding) {
                                            isHolding = false;
                                            if (!isDragging) {
                                                executeTap();
                                            }
                                        }
                                        return true;
                                }
                                return false;
                            }
                        });

                        wm.addView(macroButton, params);
                        XposedBridge.log("Flork LSPosed: Botao adicionado!");
                    } catch (Exception e) {
                        XposedBridge.log("Flork LSPosed: Erro ao criar botao: " + e.getMessage());
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
                handler.postDelayed(this, 1); // Spam de 1ms
            }
        }
    };

    private static void executeTap() {
        new Thread(() -> {
            try {
                if (suOutput != null) {
                    // KICK e POWER KICK na sequência rápida
                    suOutput.writeBytes("input tap " + KICK_X + " " + KICK_Y + "\n");
                    suOutput.writeBytes("input tap " + POWER_X + " " + POWER_Y + "\n");
                    suOutput.flush();
                }
            } catch (Exception e) {
                XposedBridge.log("Flork LSPosed: Erro no toque: " + e.getMessage());
            }
        }).start();
    }
}
