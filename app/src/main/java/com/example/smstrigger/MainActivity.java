package com.example.smstrigger;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 设置界面：号码 / 关键词 / 匹配模式 / 提醒方式 / 提醒时长 / 测试 / 日志。
 */
public class MainActivity extends Activity {
    private Switch enableSwitch;
    private EditText numberEdit;
    private EditText keywordEdit;
    private Spinner matchSpinner;
    private RadioGroup alertGroup;
    private RadioButton rbVibrate;
    private RadioButton rbRing;
    private RadioButton rbBoth;
    private EditText durationEdit;
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(20), dp(20), dp(20), dp(32));
        scroll.addView(root);
        setContentView(scroll);

        root.addView(title("短信强提醒"));
        root.addView(hint("收到指定号码或含指定关键词的短信时，按闹铃级别强提醒。\n规则每行一条；号码支持“包含”匹配，关键词忽略大小写。"));

        enableSwitch = new Switch(this);
        enableSwitch.setText("启用短信监听");
        enableSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        root.addView(enableSwitch);
        enableSwitch.setOnCheckedChangeListener((v, checked) ->
                Prefs.setBoolean(this, Prefs.KEY_ENABLED, checked));

        root.addView(section("1. 指定号码（每行一个，可留空）"));
        numberEdit = new EditText(this);
        numberEdit.setHint("例如：95588 / 13800138000 / 1069");
        numberEdit.setMinLines(3);
        numberEdit.setGravity(Gravity.TOP | Gravity.START);
        numberEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(numberEdit);

        root.addView(section("2. 指定文字 / 关键词（每行一个，可留空）"));
        keywordEdit = new EditText(this);
        keywordEdit.setHint("例如：告警 / 停机 / 学费");
        keywordEdit.setMinLines(3);
        keywordEdit.setGravity(Gravity.TOP | Gravity.START);
        keywordEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(keywordEdit);

        root.addView(section("3. 命中规则"));
        matchSpinner = new Spinner(this);
        String[] matchModes = {"任一命中：号码 或 关键词", "同时满足：号码 且 关键词"};
        ArrayAdapter<String> matchAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, matchModes);
        matchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        matchSpinner.setAdapter(matchAdapter);
        root.addView(matchSpinner);

        root.addView(section("4. 提醒方式"));
        alertGroup = new RadioGroup(this);
        alertGroup.setOrientation(RadioGroup.VERTICAL);
        rbVibrate = radio("方式1：持续震动");
        rbRing = radio("方式2：持续铃声");
        rbBoth = radio("方式3：持续震动 + 铃声");
        alertGroup.addView(rbVibrate);
        alertGroup.addView(rbRing);
        alertGroup.addView(rbBoth);
        root.addView(alertGroup);

        root.addView(section("5. 提醒时长（秒，10–3600）"));
        durationEdit = new EditText(this);
        durationEdit.setHint("默认 60 秒");
        durationEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        durationEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        root.addView(durationEdit);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button save = new Button(this);
        save.setText("保存设置");
        Button test = new Button(this);
        test.setText("测试提醒");
        save.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        test.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        buttons.addView(save);
        buttons.addView(test);
        root.addView(buttons);

        save.setOnClickListener(v -> saveAll());

        test.setOnClickListener(v -> {
            saveAll();
            AlertScheduler.trigger(this, "10086", "测试短信：短信强提醒测试");
            Toast.makeText(this, "已触发测试，按上面的时长自动停止", Toast.LENGTH_SHORT).show();
        });

        root.addView(section("鸿蒙 4.2 装机检查清单"));
        TextView guide = hint("① 设置 → 应用 → 权限：短信设为“始终允许”\n"
                + "② 通知权限、锁屏通知、全屏提醒均允许\n"
                + "③ 设置 → 应用启动管理：关闭自动管理，打开自启动 / 关联启动 / 后台活动\n"
                + "④ 电池优化设为“不受限”，多任务界面给应用加锁\n"
                + "⑤ 若关键词涉及验证码：负一屏搜索“验证码”，关闭验证码安全保护\n"
                + "⑥ 静音不影响闹铃音量；勿扰模式默认也允许闹钟");
        root.addView(guide);

        Button openSettings = new Button(this);
        openSettings.setText("打开系统设置");
        root.addView(openSettings);
        openSettings.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception ignored) {
            }
        });

        root.addView(section("报警记录"));
        logView = new TextView(this);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        logView.setTextColor(Color.parseColor("#444444"));
        root.addView(logView);

        load();
        requestPermissionsIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (logView != null) {
            String log = Prefs.getString(this, Prefs.KEY_LOG, "");
            logView.setText(TextUtils.isEmpty(log) ? "（暂无记录）" : log);
        }
    }

    private void load() {
        enableSwitch.setChecked(Prefs.getBoolean(this, Prefs.KEY_ENABLED, false));
        numberEdit.setText(Prefs.getString(this, Prefs.KEY_NUMBERS, ""));
        keywordEdit.setText(Prefs.getString(this, Prefs.KEY_KEYWORDS, ""));
        matchSpinner.setSelection(Prefs.getInt(this, Prefs.KEY_MATCH_MODE, 0));
        int mode = Prefs.getInt(this, Prefs.KEY_ALERT_MODE, 2);
        if (mode == 0) rbVibrate.setChecked(true);
        else if (mode == 1) rbRing.setChecked(true);
        else rbBoth.setChecked(true);
        durationEdit.setText(String.valueOf(Prefs.getInt(this, Prefs.KEY_DURATION, 60)));
    }

    private void saveAll() {
        Prefs.setBoolean(this, Prefs.KEY_ENABLED, enableSwitch.isChecked());
        Prefs.setString(this, Prefs.KEY_NUMBERS, numberEdit.getText().toString().trim());
        Prefs.setString(this, Prefs.KEY_KEYWORDS, keywordEdit.getText().toString().trim());
        Prefs.setInt(this, Prefs.KEY_MATCH_MODE, matchSpinner.getSelectedItemPosition());
        int mode = 2;
        if (rbVibrate.isChecked()) mode = 0;
        else if (rbRing.isChecked()) mode = 1;
        Prefs.setInt(this, Prefs.KEY_ALERT_MODE, mode);

        int seconds;
        try {
            seconds = Integer.parseInt(durationEdit.getText().toString().trim());
        } catch (Exception e) {
            seconds = 60;
        }
        seconds = Math.max(10, Math.min(3600, seconds));
        Prefs.setInt(this, Prefs.KEY_DURATION, seconds);
        durationEdit.setText(String.valueOf(seconds));
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, 2);
        }
    }

    private RadioButton radio(String text) {
        RadioButton rb = new RadioButton(this);
        rb.setText(text);
        rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        return rb;
    }

    private TextView title(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#C62828"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView section(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#222222"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView hint(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#666666"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
