package com.pedometer.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StepCounterService extends Service implements SensorEventListener {

    private static final String CHANNEL_ID = "pedometer_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String PREFS = "pedometer_prefs";

    private SensorManager sensorManager;
    private Sensor stepCounterSensor;
    private Sensor accelerometerSensor;
    private SharedPreferences prefs;

    private float initialSensorValue = -1f;
    private int todaySteps = 0;

    private long lastStepTime = 0;
    private float filteredMagnitude = 9.81f;
    private static final float STEP_THRESHOLD = 2.5f;
    private static final long MIN_STEP_INTERVAL = 250;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        loadState();
        registerSensor();
    }

    private void loadState() {
        todaySteps = prefs.getInt("todaySteps", 0);
        String savedDate = prefs.getString("todayDate", "");
        String currentDate = getTodayKey();

        if (!savedDate.equals(currentDate)) {
            if (todaySteps > 0) {
                String historyKey = "history_" + savedDate;
                prefs.edit().putInt(historyKey, todaySteps).apply();
            }
            todaySteps = 0;
            initialSensorValue = -1f;
            prefs.edit()
                .putInt("todaySteps", 0)
                .putString("todayDate", currentDate)
                .putFloat("initialSensorValue", -1f)
                .apply();
        } else {
            initialSensorValue = prefs.getFloat("initialSensorValue", -1f);
        }
    }

    private String getTodayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void registerSensor() {
        if (stepCounterSensor != null) {
            sensorManager.registerListener(this, stepCounterSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        } else if (accelerometerSensor != null) {
            sensorManager.registerListener(this, accelerometerSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            handleStepCounter(event);
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            handleAccelerometer(event);
        }
    }

    private void handleStepCounter(SensorEvent event) {
        float sensorValue = event.values[0];

        if (initialSensorValue < 0) {
            initialSensorValue = sensorValue - todaySteps;
            prefs.edit().putFloat("initialSensorValue", initialSensorValue).apply();
        }

        int newSteps = (int) (sensorValue - initialSensorValue);

        if (newSteps != todaySteps && newSteps >= 0) {
            todaySteps = newSteps;
            prefs.edit().putInt("todaySteps", todaySteps).apply();
            updateNotification(todaySteps);
            broadcastUpdate(todaySteps);
        }
    }

    private void handleAccelerometer(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);

        filteredMagnitude = 0.15f * magnitude + 0.85f * filteredMagnitude;
        float delta = magnitude - filteredMagnitude;

        long now = System.currentTimeMillis();
        if (delta > STEP_THRESHOLD && now - lastStepTime > MIN_STEP_INTERVAL) {
            lastStepTime = now;
            todaySteps++;
            prefs.edit().putInt("todaySteps", todaySteps).apply();
            updateNotification(todaySteps);
            broadcastUpdate(todaySteps);
        }
    }

    private void broadcastUpdate(int steps) {
        Intent intent = new Intent("STEP_UPDATE");
        intent.putExtra("steps", steps);
        sendBroadcast(intent);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Pedometer Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background step counting");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pedometer Active")
            .setContentText("Steps today: " + todaySteps)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }

    private void updateNotification(int steps) {
        NotificationManager manager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        Intent restart = new Intent(this, StepCounterService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart);
        }
    }
}