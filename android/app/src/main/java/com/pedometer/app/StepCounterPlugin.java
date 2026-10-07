package com.pedometer.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "StepCounter")
public class StepCounterPlugin extends Plugin {

    private BroadcastReceiver stepReceiver;
    private SharedPreferences prefs;

    @Override
    public void load() {
        prefs = getContext().getSharedPreferences("pedometer_prefs", Context.MODE_PRIVATE);

        stepReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int steps = intent.getIntExtra("steps", 0);
                JSObject data = new JSObject();
                data.put("steps", steps);
                notifyListeners("stepUpdate", data);
            }
        };

        IntentFilter filter = new IntentFilter("STEP_UPDATE");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getContext().registerReceiver(stepReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            getContext().registerReceiver(stepReceiver, filter);
        }
    }

    @PluginMethod
    public void start(PluginCall call) {
        Intent intent = new Intent(getContext(), StepCounterService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getContext().startForegroundService(intent);
        } else {
            getContext().startService(intent);
        }
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        Intent intent = new Intent(getContext(), StepCounterService.class);
        getContext().stopService(intent);
        call.resolve();
    }

    @PluginMethod
    public void getTodaySteps(PluginCall call) {
        int steps = prefs.getInt("todaySteps", 0);
        JSObject result = new JSObject();
        result.put("steps", steps);
        call.resolve(result);
    }

    @PluginMethod
    public void resetToday(PluginCall call) {
        prefs.edit()
            .putInt("todaySteps", 0)
            .putFloat("initialSensorValue", -1f)
            .apply();

        Intent intent = new Intent("STEP_UPDATE");
        intent.putExtra("steps", 0);
        getContext().sendBroadcast(intent);

        call.resolve();
    }

    @PluginMethod
    public void getHistory(PluginCall call) {
        JSObject history = new JSObject();
        java.util.Map<String, ?> all = prefs.getAll();
        for (java.util.Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith("history_")) {
                String date = entry.getKey().replace("history_", "");
                history.put(date, entry.getValue());
            }
        }
        JSObject result = new JSObject();
        result.put("history", history);
        call.resolve(result);
    }

    @Override
    protected void handleOnDestroy() {
        if (stepReceiver != null) {
            try {
                getContext().unregisterReceiver(stepReceiver);
            } catch (Exception ignored) {}
        }
        super.handleOnDestroy();
    }
}