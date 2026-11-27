package com.example.smstrigger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsMessage;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 被动监听系统短信广播，平时不常驻后台。
 * 号码命中 或 关键词命中 即触发强提醒（可在设置里改为“同时满足”）。
 */
public class SmsReceiver extends BroadcastReceiver {
    private static final String LEGACY_ACTION = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (!LEGACY_ACTION.equals(action)) return;
        if (!Prefs.getBoolean(context, Prefs.KEY_ENABLED, false)) return;

        Object[] pdus = null;
        if (intent.getExtras() != null) {
            pdus = (Object[]) intent.getExtras().get("pdus");
        }
        if (pdus == null || pdus.length == 0) return;

        String format = intent.getStringExtra("format");
        String sender = "";
        StringBuilder body = new StringBuilder();
        for (Object pdu : pdus) {
            if (!(pdu instanceof byte[])) continue;
            SmsMessage msg = format == null
                    ? SmsMessage.createFromPdu((byte[]) pdu)
                    : SmsMessage.createFromPdu((byte[]) pdu, format);
            if (msg == null) continue;
            if (TextUtils.isEmpty(sender)) {
                sender = msg.getOriginatingAddress() == null ? "" : msg.getOriginatingAddress();
            }
            String part = msg.getMessageBody();
            if (part != null) body.append(part);
        }

        String text = body.toString();
        if (matches(context, sender, text)) {
            AlertScheduler.trigger(context, sender, text);
        }
    }

    static boolean matches(Context context, String sender, String body) {
        List<String> numbers = splitRules(Prefs.getString(context, Prefs.KEY_NUMBERS, ""));
        List<String> keywords = splitRules(Prefs.getString(context, Prefs.KEY_KEYWORDS, ""));
        if (numbers.isEmpty() && keywords.isEmpty()) return false;

        String senderDigits = digits(sender);
        boolean numberHit = false;
        for (String raw : numbers) {
            String d = digits(raw);
            if (d.length() < 4) continue;
            if (senderDigits.contains(d)
                    || (senderDigits.length() >= 4 && d.length() >= 6 && d.contains(senderDigits))) {
                numberHit = true;
                break;
            }
        }

        String lowerBody = body == null ? "" : body.toLowerCase(Locale.ROOT);
        boolean keywordHit = false;
        for (String raw : keywords) {
            String k = raw.trim().toLowerCase(Locale.ROOT);
            if (!k.isEmpty() && lowerBody.contains(k)) {
                keywordHit = true;
                break;
            }
        }

        int mode = Prefs.getInt(context, Prefs.KEY_MATCH_MODE, 0);
        if (mode == 1) {
            return !numbers.isEmpty() && !keywords.isEmpty() && numberHit && keywordHit;
        }
        return numberHit || keywordHit;
    }

    private static List<String> splitRules(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        String[] parts = raw.split("[\n\r,;，；]+");
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** 去掉 +、空格、横线，并兼容 +86 前缀。 */
    private static String digits(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        String d = sb.toString();
        if (d.length() > 4 && d.startsWith("86")) d = d.substring(2);
        return d;
    }
}
