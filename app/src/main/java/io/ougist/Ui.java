package io.ougist;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** 画面をコードで組み立てるための小さな道具箱。 */
final class Ui {

    interface OnValue {
        void on(int value);
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** 丸い角の面。設定画面の 1 区画。 */
    static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(c.getColor(R.color.card));
        bg.setCornerRadius(dp(c, 18));
        l.setBackground(bg);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 12);
        l.setLayoutParams(lp);
        return l;
    }

    static TextView heading(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(c.getColor(R.color.text_sub));
        t.setAllCaps(false);
        t.setPadding(0, 0, 0, dp(c, 10));
        return t;
    }

    static TextView title(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTextColor(c.getColor(R.color.text));
        return t;
    }

    static TextView body(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(c.getColor(R.color.text_sub));
        t.setLineSpacing(dp(c, 3), 1f);
        return t;
    }

    static Button button(Context c, String s, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    static Switch toggle(Context c, String s, boolean on,
                         android.widget.CompoundButton.OnCheckedChangeListener l) {
        Switch sw = new Switch(c);
        sw.setText(s);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sw.setTextColor(c.getColor(R.color.text));
        sw.setChecked(on);
        sw.setOnCheckedChangeListener(l);
        sw.setPadding(0, dp(c, 6), 0, dp(c, 6));
        return sw;
    }

    /** 見出し + 現在値 + つまみ。値が動くたびに cb を呼ぶ。 */
    static View slider(final Context c, String label, final String unit,
                       int min, int max, int value, final OnValue cb) {
        LinearLayout box = column(c);
        box.setPadding(0, dp(c, 8), 0, dp(c, 4));

        LinearLayout head = row(c);
        TextView name = title(c, label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        head.addView(name, lp);
        final TextView val = new TextView(c);
        val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        val.setTextColor(c.getColor(R.color.accent));
        val.setText(value + unit);
        head.addView(val);
        box.addView(head);

        SeekBar bar = new SeekBar(c);
        bar.setMin(min);
        bar.setMax(max);
        bar.setProgress(value);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                val.setText(progress + unit);
                if (fromUser) cb.on(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        box.addView(bar);
        return box;
    }

    static View divider(Context c) {
        View v = new View(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.5f)));
        lp.topMargin = dp(c, 10);
        lp.bottomMargin = dp(c, 4);
        v.setLayoutParams(lp);
        v.setBackgroundColor(c.getColor(R.color.divider));
        return v;
    }

    static View space(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, h)));
        return v;
    }

    /** ステータスバーとナビゲーションバーの下に潜らないよう余白を入れる。 */
    static void fitSystemBars(final View target, final int extraTopDp, final int extraBottomDp) {
        final Context c = target.getContext();
        target.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets i = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = i.top;
                bottom = i.bottom;
                left = i.left;
                right = i.right;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            v.setPadding(left + dp(c, 16), top + dp(c, extraTopDp),
                    right + dp(c, 16), bottom + dp(c, extraBottomDp));
            return insets;
        });
        target.requestApplyInsets();
    }

    static GradientDrawable pill(Context c, int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static int alpha(int color, float a) {
        return Color.argb(Math.round(255 * a), Color.red(color), Color.green(color), Color.blue(color));
    }

    private Ui() {
    }
}
