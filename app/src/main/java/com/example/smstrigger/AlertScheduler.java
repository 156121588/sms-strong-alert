package com.example.smstrigger;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 命中规则后：写日志 -> 用精确闹钟唤起报警服务 -> 立即弹全屏提醒兜底。
 * 精确闹钟使用 setAlarmClock，属于 Android 12+ 后台启动前台服务的豁免场景。
 */
public final class AlertScheduler {
    public static final String CHANNEL_ID = "sms_strong_alert";
    private static final String TAG = "AlertScheduler";
    private static final int REQUEST_ALARM = 1001;
    private static final int REQUEST_FULL = 1002;
    private static final int NOTIFICATION_ID = 2001;

    private AlertScheduler() {
    }

    public static void trigger(Context context, String sender, String body) {
        long now = System.currentTimeMillis();
        String s = sender == null ? "" : sender;
        String b = body == null ? "" : body;

        String key = s + "|" + b;
        long lastTime = Prefs.getLong(context, Prefs.KEY_LAST_TIME, 0L);
        String lastKey = Prefs.getString(context, Prefs.KEY_LAST_KEY, "");
        // 长短信分多条或系统重复广播时，8 秒内相同内容只提醒一次
        if (key.equals(lastKey) && now - lastTime < 8000L) return;
        Prefs.setString(context, Prefs.KEY_LAST_KEY, key);
        Prefs.setLong(context, Prefs.KEY_LAST_TIME, now);

        String time = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(now));
        String brief = b.length() > 30 ? b.substring(0, 30) + "…" : b;
        Prefs.appendLog(context, time + "  " + (s.isEmpty() ? "未知号码" : s) + "：" + brief);

        Intent fire = new Intent(context, AlarmTriggerReceiver.class)
                .setAction(AlarmTriggerReceiver.ACTION_FIRE);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent alarmPi = PendingIntent.getBroadcast(context, REQUEST_ALARM, fire, flags);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            long at = now + 800L;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, alarmPi), alarmPi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, alarmPi);
            }
        }

        showFullScreenNotification(context, s, b);
    }

    public static void showFullScreenNotification(Context context, String sender, String body) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        createChannel(context);

        Intent ui = new Intent(context, AlarmActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fullPi = PendingIntent.getActivity(context, REQUEST_FULL, ui, flags);
        PendingIntent contentPi = PendingIntent.getActivity(context, REQUEST_FULL + 1, ui, flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("短信强提醒：" + (sender == null || sender.isEmpty() ? "未知号码" : sender))
                .setContentText(body == null ? "" : body)
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(contentPi);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setFullScreenIntent(fullPi, true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setPriority(Notification.PRIORITY_HIGH);
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }
        try {
            nm.notify(NOTIFICATION_ID, builder.build());
        } catch (Exception e) {
            Log.e(TAG, "show notification failed", e);
        }
    }

    public static void cancelNotification(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }

    static void createChannel(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "短信强提醒", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("命中规则后的全屏提醒");
        ch.enableVibration(false);
        ch.setSound(null, null);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }
}
