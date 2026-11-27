package com.example.smstrigger;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 锁屏全屏提醒界面：命中后自动弹出，点“停止提醒”即可结束。
 */
public class AlarmActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("短信强提醒");
        title.setTextColor(Color.parseColor("#C62828"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("收到符合规则的短信\n闹铃 / 震动提醒已启动");
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        desc.setTextColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        desc.setLayoutParams(lp);
        desc.setGravity(android.view.Gravity.CENTER);
        root.addView(desc);

        Button stop = new Button(this);
        stop.setText("停止提醒");
        stop.setTextColor(Color.WHITE);
        stop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        stop.setBackgroundColor(Color.parseColor("#C62828"));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(36);
        stop.setLayoutParams(bp);
        root.addView(stop);

        setContentView(root);

        // 确保报警服务在运行（重复触发时不会重复播放）
        Intent start = new Intent(this, AlertService.class).setAction(AlertService.ACTION_START);
        try {
            startForegroundService(start);
        } catch (Exception e) {
            try {
                startService(start);
            } catch (Exception ignored) {
            }
        }

        stop.setOnClickListener(v -> {
            Intent stopIntent = new Intent(this, AlertService.class)
                    .setAction(AlertService.ACTION_STOP);
            try {
                startService(stopIntent);
            } catch (Exception ignored) {
            }
            finish();
        });
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
