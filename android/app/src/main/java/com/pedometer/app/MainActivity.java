package com.pedometer.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BridgeActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // ثبت پلاگین قبل از super
        registerPlugin(StepCounterPlugin.class);

        super.onCreate(savedInstanceState);

        // درخواست مجوزها
        requestPermissions();

        // درخواست معافیت از بهینه‌سازی باتری
        requestBatteryOptimizationExemption();

        // راه‌اندازی سرویس پس‌زمینه
        startStepCounterService();
    }

    /**
     * درخواست مجوزهای ضروری
     */
    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();

        // مجوز ACTIVITY_RECOGNITION (اندروید ۱۰ و بالاتر)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
            }
        }

        // مجوز POST_NOTIFICATIONS (اندروید ۱۳ و بالاتر)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toArray(new String[0]),
                PERMISSION_REQUEST_CODE
            );
        }
    }

    /**
     * درخواست معافیت از بهینه‌سازی باتری
     * این برای کارکرد بهتر در پس‌زمینه ضروری است
     */
    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null &&
                !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    // اگه نشد، کاربر می‌تونه دستی از تنظیمات فعال کنه
                }
            }
        }
    }

    /**
     * راه‌اندازی سرویس شمارش قدم در پس‌زمینه
     */
    private void startStepCounterService() {
        Intent serviceIntent = new Intent(this, StepCounterService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
}