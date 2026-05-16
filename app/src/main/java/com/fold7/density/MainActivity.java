package com.fold7.density;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int SHIZUKU_PERMISSION_REQUEST = 7;
    private static final int DEFAULT_DISPLAY_0_DENSITY = 550;
    private static final int DEFAULT_DISPLAY_1_DENSITY = 455;

    private TextView statusView;
    private TextView logView;
    private EditText density0Input;
    private EditText density1Input;
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
        if (Shizuku.pingBinder()) {
            setStatus("Shizuku найден. Нажми доступ, потом подключить.");
        } else if (isShizukuInstalled()) {
            setStatus("Shizuku установлен, но не запущен. Запускаю Shizuku...");
            launchShizukuApp();
        } else {
            setStatus("Shizuku не установлен. Откройте страницу загрузки Shizuku.");
            launchShizukuApp();
        }
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

        LinearLayout densityLayout = new LinearLayout(this);
        densityLayout.setOrientation(LinearLayout.HORIZONTAL);
        densityLayout.setWeightSum(2f);

        density0Input = makeEditText(String.valueOf(DEFAULT_DISPLAY_0_DENSITY));
        density1Input = makeEditText(String.valueOf(DEFAULT_DISPLAY_1_DENSITY));

        densityLayout.addView(makeLabeledInput("Display 0", density0Input), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        secondParams.setMargins(dp(8), 0, 0, 0);
        densityLayout.addView(makeLabeledInput("Display 1", density1Input), secondParams);

        root.addView(densityLayout, matchWrap());

        permissionButton = makeButton("Дать доступ Shizuku");
        permissionButton.setOnClickListener(v -> requestShizukuPermission());
        root.addView(permissionButton, matchWrap());

        bindButton = makeButton("Подключить сервис");
        bindButton.setOnClickListener(v -> bindDensityService());
        root.addView(bindButton, matchWrap());

        applyButton = makeButton("Применить");
        applyButton.setOnClickListener(v -> applyDensityPreset());
        root.addView(applyButton, matchWrap());

        resetButton = makeButton("Сбросить density");
        resetButton.setOnClickListener(v -> resetDensityPreset());
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

    private EditText makeEditText(String text) {
        EditText editText = new EditText(this);
        editText.setText(text);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setTextSize(16);
        return editText;
    }

    private LinearLayout makeLabeledInput(String labelText, EditText editText) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextSize(14);
        label.setTextColor(Color.rgb(36, 48, 60));
        layout.addView(label);

        layout.addView(editText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return layout;
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

    private void applyDensityPreset() {
        if (densityService == null) {
            setStatus("Сначала подключи сервис.");
            refreshButtons();
            return;
        }

        int density0 = parseDensityValue(density0Input, DEFAULT_DISPLAY_0_DENSITY);
        int density1 = parseDensityValue(density1Input, DEFAULT_DISPLAY_1_DENSITY);

        setButtonsEnabled(false);
        new Thread(() -> {
            String result;
            try {
                result = densityService.applyFoldPreset(density0, density1);
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

    private void resetDensityPreset() {
        if (densityService == null) {
            setStatus("Сначала подключи сервис.");
            refreshButtons();
            return;
        }

        setButtonsEnabled(false);
        new Thread(() -> {
            String result;
            try {
                result = densityService.resetDensity();
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

    private int parseDensityValue(EditText input, int fallback) {
        try {
            String text = input.getText().toString().trim();
            return text.isEmpty() ? fallback : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void launchShizukuApp() {
        String[] launchPackages = {
                "rikka.shizuku",
                "rikka.shizuku.manager",
                "moe.shizuku.manager"
        };

        for (String pkg : launchPackages) {
            try {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launchIntent != null) {
                    setStatus("Запускаю Shizuku...");
                    startActivity(launchIntent);
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        List<String> installedShizuku = getInstalledShizukuPackages();
        if (!installedShizuku.isEmpty()) {
            String packageInfo = installedShizuku.get(0);
            setStatus("Shizuku установлен, но не запущен. Откройте его вручную.");
            Intent settingsIntent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageInfo));
            startActivity(settingsIntent);
            return;
        }

        setStatus("Shizuku не установлен. Откройте страницу загрузки Shizuku.");
        Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/RikkaApps/Shizuku/releases"));
        startActivity(browserIntent);
    }

    private boolean isShizukuInstalled() {
        return !getInstalledShizukuPackages().isEmpty();
    }

    private List<String> getInstalledShizukuPackages() {
        try {
            List<PackageInfo> packages = getPackageManager().getInstalledPackages(0);
            List<String> installed = new java.util.ArrayList<>();
            for (PackageInfo pkg : packages) {
                if (pkg.packageName.contains("shizuku")) {
                    installed.add(pkg.packageName);
                }
            }
            return installed;
        } catch (Exception ignored) {
        }
        return new java.util.ArrayList<>();
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
