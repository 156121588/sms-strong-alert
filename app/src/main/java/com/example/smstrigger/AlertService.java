package com.example.smstrigger;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;

import android.media.AudioManager;

/**
 * 强提醒核心：闹铃音频流（静音/勿扰下仍可响）+ 循环震动 + 唤醒锁 + 前台服务。
 * 到设定时长自动停止，并恢复原来的闹铃音量。
 */
public class AlertService extends Service {
    public static final String ACTION_START = "com.example.smstrigger.ALERT_START";
    public static final String ACTION_STOP = "com.example.smstrigger.ALERT_STOP";

    private static final String TAG = "AlertService";
    private static final int NOTIFICATION_ID = 3001;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable stopRunnable;
    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private AudioManager audioManager;
    private int oldAlarmVolume = -1;
    private boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        AlertScheduler.createChannel(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopAlert();
            return START_NOT_STICKY;
        }
        startAlert();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressWarnings("deprecation")
    private void startAlert() {
        if (running) return;
        running = true;

        final int durationSec = Math.max(10, Math.min(3600,
                Prefs.getInt(this, Prefs.KEY_DURATION, 60)));
        final int mode = Prefs.getInt(this, Prefs.KEY_ALERT_MODE, 2); // 0震动 1铃声 2两者

        startForeground(NOTIFICATION_ID, buildNotification());

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "sms_strong_alert:alert");
            wakeLock.acquire(durationSec * 1000L + 15000L);
        }

        if (audioManager != null) {
            try {
                oldAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);
                audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            } catch (Exception e) {
                Log.w(TAG, "audio setup failed", e);
            }
        }

        // 持续震动：2 秒一个循环，直到停止
        if ((mode == 0 || mode == 2) && vibrator != null && vibrator.hasVibrator()) {
            try {
                long[] pattern = {0, 1200, 300, 1200, 300};
                vibrator.vibrate(pattern, 0);
            } catch (Exception e) {
                Log.w(TAG, "vibration failed", e);
            }
        }

        // 持续铃声：走系统闹铃铃声，使用 ALARM 音频流
        if (mode == 1 || mode == 2) {
            try {
                Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (uri != null) {
                    player = new MediaPlayer();
                    player.setDataSource(this, uri);
                    AudioAttributes attrs = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build();
                    player.setAudioAttributes(attrs);
                    player.setLooping(true);
                    player.prepare();
                    player.start();
                }
            } catch (Exception e) {
                Log.w(TAG, "ringtone failed", e);
                releasePlayer();
            }
        }

        stopRunnable = new Runnable() {
            @Override
            public void run() {
                stopAlert();
            }
        };
        handler.postDelayed(stopRunnable, durationSec * 1000L);
    }

    private Notification buildNotification() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        Intent stopIntent = new Intent(this, AlertService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 4001, stopIntent, flags);

        Intent ui = new Intent(this, AlarmActivity.class);
        PendingIntent uiPi = PendingIntent.getActivity(this, 4002, ui, flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, AlertScheduler.CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("短信强提醒进行中")
                .setContentText("点击可停止，或等待设定时长自动停止")
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(uiPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止提醒", stopPi);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_ALARM);
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }
        return builder.build();
    }

    private void stopAlert() {
        handler.removeCallbacks(stopRunnable);
        stopRunnable = null;
        cleanupResources();
        AlertScheduler.cancelNotification(this);
        running = false;
        try {
            stopForeground(true);
        } catch (Exception ignored) {
        }
        stopSelf();
    }

    private void cleanupResources() {
        releasePlayer();
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception ignored) {
            }
        }
        if (audioManager != null) {
            try {
                if (oldAlarmVolume >= 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, oldAlarmVolume, 0);
                    oldAlarmVolume = -1;
                }
                audioManager.abandonAudioFocus(null);
            } catch (Exception ignored) {
            }
        }
        if (wakeLock != null) {
            try {
                if (wakeLock.isHeld()) wakeLock.release();
            } catch (Exception ignored) {
            }
            wakeLock = null;
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
            } catch (Exception ignored) {
            }
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        cleanupResources();
        running = false;
        super.onDestroy();
    }
}
