package com.example.smstrigger;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 所有配置与报警日志都保存在本机，不联网、不上传。
 */
public final class Prefs {
    private static final String NAME = "sms_alert_prefs";

    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_NUMBERS = "numbers";
    public static final String KEY_KEYWORDS = "keywords";
    public static final String KEY_MATCH_MODE = "match_mode";   // 0=任一命中(OR), 1=同时满足(AND)
    public static final String KEY_ALERT_MODE = "alert_mode";   // 0=震动, 1=铃声, 2=震动+铃声
    public static final String KEY_DURATION = "duration_sec";
    public static final String KEY_LOG = "log";
    public static final String KEY_LAST_KEY = "last_key";
    public static final String KEY_LAST_TIME = "last_time";

    private Prefs() {
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static boolean getBoolean(Context c, String k, boolean def) {
        return p(c).getBoolean(k, def);
    }

    public static void setBoolean(Context c, String k, boolean v) {
        p(c).edit().putBoolean(k, v).apply();
    }

    public static String getString(Context c, String k, String def) {
        return p(c).getString(k, def);
    }

    public static void setString(Context c, String k, String v) {
        p(c).edit().putString(k, v).apply();
    }

    public static int getInt(Context c, String k, int def) {
        return p(c).getInt(k, def);
    }

    public static void setInt(Context c, String k, int v) {
        p(c).edit().putInt(k, v).apply();
    }

    public static long getLong(Context c, String k, long def) {
        return p(c).getLong(k, def);
    }

    public static void setLong(Context c, String k, long v) {
        p(c).edit().putLong(k, v).apply();
    }

    public static void appendLog(Context c, String line) {
        String old = getString(c, KEY_LOG, "");
        String updated = line + "\n" + old;
        String[] lines = updated.split("\n");
        if (lines.length > 30) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 30; i++) {
                sb.append(lines[i]).append('\n');
            }
            updated = sb.toString();
        }
        setString(c, KEY_LOG, updated);
    }
}
