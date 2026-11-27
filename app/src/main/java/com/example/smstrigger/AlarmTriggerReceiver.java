package com.example.smstrigger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 精确闹钟到点后唤起报警前台服务；同时尝试点亮锁屏界面。
 */
public class AlarmTriggerReceiver extends BroadcastReceiver {
    public static final String ACTION_FIRE = "com.example.smstrigger.FIRE_ALARM";
    private static final String TAG = "AlarmTriggerReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent service = new Intent(context, AlertService.class)
                .setAction(AlertService.ACTION_START);
        try {
            context.startForegroundService(service);
        } catch (Exception e) {
            Log.w(TAG, "startForegroundService failed, fallback to startService", e);
            try {
                context.startService(service);
            } catch (Exception ignored) {
            }
        }

        Intent ui = new Intent(context, AlarmActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(ui);
        } catch (Exception ignored) {
            // 后台启动 Activity 受系统限制时，全屏通知仍可兜底
        }
    }
}
