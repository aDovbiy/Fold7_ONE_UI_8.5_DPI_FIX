package com.fold7.density;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int SHIZUKU_PERMISSION_REQUEST = 7;

    private TextView statusView;
    private TextView logView;
    private Button permissionButton;
    private Button bindButton;
    private Button applyButton;
    private Button resetButton;

    private IFoldDensityService densityService;

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode == SHIZUKU_PERMISSION_REQUEST) {
                    setStatus(grantResult == PackageManager.PERMISSION_GRANTED
                            ? "Shizuku доступ разрешен."
                            : "Shizuku доступ не разрешен.");
                    refreshButtons();
                }
            };

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        setStatus("Shizuku запущен. Можно дать доступ и подключиться.");
        refreshButtons();
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        densityService = null;
        setStatus("Shizuku остановлен. Запусти Shizuku снова.");
        refreshButtons();
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            densityService = IFoldDensityService.Stub.asInterface(binder);
            setStatus("Сервис подключен. Готово к применению.");
            refreshButtons();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            densityService = null;
            setStatus("Сервис отключен.");
            refreshButtons();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        Shizuku.addRequestPermissionResultListener(permissionResultListener);
        Shizuku.addBinderReceivedListener(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);

        refreshButtons();
        setStatus(Shizuku.pingBinder()
                ? "Shizuku найден. Нажми доступ, потом подключить."
                : "Shizuku не запущен. Запусти его через Wireless debugging.");
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackgroundColor(Color.rgb(250, 250, 250));

        TextView title = new TextView(this);
        title.setText("Fold 7 Density");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(20, 32, 34));
        title.setGravity(Gravity.START);
        root.addView(title, matchWrap());

        TextView preset = new TextView(this);
        preset.setText("Display 0: 550 dpi\nDisplay 1: 455 dpi");
        preset.setTextSize(17);
        preset.setTextColor(Color.rgb(56, 68, 72));
        preset.setPadding(0, dp(12), 0, dp(16));
        root.addView(preset, matchWrap());

        statusView = new TextView(this);
        statusView.setTextSize(16);
        statusView.setTextColor(Color.rgb(24, 84, 76));
        statusView.setPadding(0, 0, 0, dp(14));
        root.addView(statusView, matchWrap());

        permissionButton = makeButton("Дать доступ Shizuku");
        permissionButton.setOnClickListener(v -> requestShizukuPermission());
        root.addView(permissionButton, matchWrap());

        bindButton = makeButton("Подключить сервис");
        bindButton.setOnClickListener(v -> bindDensityService());
        root.addView(bindButton, matchWrap());

        applyButton = makeButton("Применить 550 / 455");
        applyButton.setOnClickListener(v -> runCommand(true));
        root.addView(applyButton, matchWrap());

        resetButton = makeButton("Сбросить density");
        resetButton.setOnClickListener(v -> runCommand(false));
        root.addView(resetButton, matchWrap());

        logView = new TextView(this);
        logView.setTextSize(13);
        logView.setTextColor(Color.rgb(28, 28, 28));
        logView.setPadding(0, dp(14), 0, 0);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(logView);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(16);
        return button;
    }

    private void requestShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            setStatus("Shizuku не запущен.");
            refreshButtons();
            return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            setStatus("Shizuku доступ уже есть.");
            refreshButtons();
            return;
        }
        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST);
    }

    private void bindDensityService() {
        if (!hasShizukuPermission()) {
            setStatus("Сначала дай доступ Shizuku.");
            refreshButtons();
            return;
        }

        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName(BuildConfig.APPLICATION_ID, DensityUserService.class.getName()))
                .daemon(false)
                .processNameSuffix("density")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE);

        Shizuku.bindUserService(args, serviceConnection);
        setStatus("Подключаю сервис...");
    }

    private void runCommand(boolean applyPreset) {
        if (densityService == null) {
            setStatus("Сначала подключи сервис.");
            refreshButtons();
            return;
        }

        setButtonsEnabled(false);
        new Thread(() -> {
            String result;
            try {
                result = applyPreset
                        ? densityService.applyFoldPreset()
                        : densityService.resetDensity();
            } catch (RemoteException e) {
                result = "Ошибка сервиса: " + e.getMessage();
                densityService = null;
            }
            String finalResult = result;
            runOnUiThread(() -> {
                logView.setText(finalResult);
                setStatus("Готово.");
                refreshButtons();
            });
        }).start();
    }

    private boolean hasShizukuPermission() {
        return Shizuku.pingBinder()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    }

    private void refreshButtons() {
        boolean shizukuAlive = Shizuku.pingBinder();
        boolean hasPermission = hasShizukuPermission();
        permissionButton.setEnabled(shizukuAlive && !hasPermission);
        bindButton.setEnabled(hasPermission && densityService == null);
        applyButton.setEnabled(densityService != null);
        resetButton.setEnabled(densityService != null);
    }

    private void setButtonsEnabled(boolean enabled) {
        permissionButton.setEnabled(enabled);
        bindButton.setEnabled(enabled);
        applyButton.setEnabled(enabled);
        resetButton.setEnabled(enabled);
    }

    private void setStatus(String text) {
        statusView.setText(text);
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
