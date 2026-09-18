package io.ougist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** ougist の設定画面。 */
public class MainActivity extends Activity implements FanView.Host {

    private Config cfg;

    private TextView status;
    private TextView statusHint;
    private Button leftBtn, rightBtn;
    private FrameLayout previewBox;
    private FanView preview;
    private final Button[] hapticBtns = new Button[4];
    private final Button[] ringBtns = new Button[Config.MAX_RINGS];

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = Config.load(this);
        setContentView(build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg = Config.load(this);
        refreshStatus();
        paintRingButtons();
        refreshSlotButtons();
        previewBox.post(this::refreshPreview);
    }

    // ------------------------------------------------------------ 組み立て

    private View build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(getColor(R.color.bg));
        sv.setClipToPadding(false);
        LinearLayout root = Ui.column(this);
        sv.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Ui.fitSystemBars(sv, 16, 24);

        TextView h = new TextView(this);
        h.setText(R.string.app_name);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        h.setTextColor(getColor(R.color.text));
        h.setPadding(0, 0, 0, Ui.dp(this, 4));
        root.addView(h);
        TextView sub = Ui.body(this, getString(R.string.usage));
        sub.setPadding(0, 0, 0, Ui.dp(this, 16));
        root.addView(sub);

        root.addView(statusCard());
        root.addView(slotsCard());
        root.addView(lookCard());
        root.addView(feelCard());
        root.addView(Ui.space(this, 8));
        return sv;
    }

    private View statusCard() {
        LinearLayout c = Ui.card(this);
        status = Ui.title(this, "");
        c.addView(status);
        statusHint = Ui.body(this, "");
        statusHint.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        c.addView(statusHint);
        c.addView(Ui.button(this, getString(R.string.svc_open), v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable ignore) {
            }
        }));
        TextView side = Ui.body(this, getString(R.string.svc_hint_sideload));
        side.setPadding(0, Ui.dp(this, 10), 0, 0);
        c.addView(side);
        return c;
    }

    private View slotsCard() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.heading(this, getString(R.string.sec_slots)));

        c.addView(Ui.toggle(this, getString(R.string.use_left), cfg.leftEnabled, (v, on) -> {
            cfg.leftEnabled = on;
            cfg.save(this);
        }));
        leftBtn = Ui.button(this, getString(R.string.edit_left),
                v -> startActivity(new Intent(this, SlotListActivity.class).putExtra("left", true)));
        c.addView(leftBtn);

        c.addView(Ui.divider(this));

        c.addView(Ui.toggle(this, getString(R.string.use_right), cfg.rightEnabled, (v, on) -> {
            cfg.rightEnabled = on;
            cfg.save(this);
        }));
        rightBtn = Ui.button(this, getString(R.string.edit_right),
                v -> startActivity(new Intent(this, SlotListActivity.class).putExtra("left", false)));
        c.addView(rightBtn);
        return c;
    }

    private View lookCard() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.heading(this, getString(R.string.sec_look)));

        previewBox = new FrameLayout(this);
        previewBox.setBackground(Ui.pill(this, 0xFF3C4250, 14));
        previewBox.setClipToOutline(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 210));
        lp.bottomMargin = Ui.dp(this, 6);
        previewBox.setLayoutParams(lp);
        preview = new FanView(this, this);
        previewBox.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        c.addView(previewBox);

        c.addView(Ui.title(this, getString(R.string.p_rings)));
        LinearLayout ringRow = Ui.row(this);
        ringRow.setPadding(0, Ui.dp(this, 8), 0, 0);
        String[] ringNames = {getString(R.string.rings_1), getString(R.string.rings_2)};
        for (int i = 0; i < Config.MAX_RINGS; i++) {
            final int count = i + 1;
            Button b = Ui.button(this, ringNames[i], v -> {
                cfg.rings = count;
                cfg.save(this);
                paintRingButtons();
                refreshSlotButtons();
                refreshPreview();
            });
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            rlp.rightMargin = i < Config.MAX_RINGS - 1 ? Ui.dp(this, 6) : 0;
            ringRow.addView(b, rlp);
            ringBtns[i] = b;
        }
        c.addView(ringRow);
        paintRingButtons();
        TextView ringHint = Ui.body(this, getString(R.string.p_rings_hint));
        ringHint.setPadding(0, Ui.dp(this, 6), 0, 0);
        c.addView(ringHint);

        c.addView(Ui.slider(this, getString(R.string.p_radius), "dp", 70, 220, cfg.radiusDp, v -> {
            cfg.radiusDp = v;
            saveAndPreview();
        }));
        c.addView(Ui.slider(this, getString(R.string.p_icon), "dp", 28, 72, cfg.iconDp, v -> {
            cfg.iconDp = v;
            saveAndPreview();
        }));
        c.addView(Ui.slider(this, getString(R.string.p_span), "°", 60, 180, cfg.spanDeg, v -> {
            cfg.spanDeg = v;
            saveAndPreview();
        }));
        c.addView(Ui.slider(this, getString(R.string.p_dim), "%", 0, 60, cfg.dimPct, v -> {
            cfg.dimPct = v;
            saveAndPreview();
        }));
        c.addView(Ui.toggle(this, getString(R.string.p_label), cfg.showLabel, (v, on) -> {
            cfg.showLabel = on;
            saveAndPreview();
        }));
        return c;
    }

    private View feelCard() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.heading(this, getString(R.string.sec_feel)));

        c.addView(Ui.slider(this, getString(R.string.p_trigger_w), "dp", 6, 48, cfg.triggerWidthDp, v -> {
            cfg.triggerWidthDp = v;
            cfg.save(this);
        }));
        c.addView(Ui.slider(this, getString(R.string.p_trigger_top), "%", 0, 45, cfg.triggerTopPct, v -> {
            cfg.triggerTopPct = v;
            cfg.save(this);
        }));
        c.addView(Ui.slider(this, getString(R.string.p_trigger_bottom), "%", 0, 45, cfg.triggerBottomPct, v -> {
            cfg.triggerBottomPct = v;
            cfg.save(this);
        }));
        c.addView(Ui.slider(this, getString(R.string.p_activate), "dp", 8, 48, cfg.activateDp, v -> {
            cfg.activateDp = v;
            cfg.save(this);
        }));

        c.addView(Ui.divider(this));
        c.addView(Ui.title(this, getString(R.string.p_haptic)));
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 8), 0, 0);
        String[] names = {getString(R.string.h_off), getString(R.string.h_light),
                getString(R.string.h_medium), getString(R.string.h_strong)};
        for (int i = 0; i < 4; i++) {
            final int level = i;
            Button b = Ui.button(this, names[i], v -> {
                cfg.haptic = level;
                cfg.save(this);
                paintHapticButtons();
                sampleVibration(level);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = i < 3 ? Ui.dp(this, 6) : 0;
            row.addView(b, lp);
            hapticBtns[i] = b;
        }
        c.addView(row);
        paintHapticButtons();
        return c;
    }

    // -------------------------------------------------------------- 更新

    private void saveAndPreview() {
        cfg.save(this);
        refreshPreview();
    }

    private void paintHapticButtons() {
        for (int i = 0; i < hapticBtns.length; i++) {
            boolean on = i == cfg.haptic;
            hapticBtns[i].setBackground(Ui.pill(this,
                    on ? getColor(R.color.accent) : Ui.alpha(getColor(R.color.text_sub), 0.16f), 10));
            hapticBtns[i].setTextColor(on ? Color.WHITE : getColor(R.color.text));
        }
    }

    private void paintRingButtons() {
        for (int i = 0; i < ringBtns.length; i++) {
            boolean on = i + 1 == cfg.rings;
            ringBtns[i].setBackground(Ui.pill(this,
                    on ? getColor(R.color.accent) : Ui.alpha(getColor(R.color.text_sub), 0.16f), 10));
            ringBtns[i].setTextColor(on ? Color.WHITE : getColor(R.color.text));
        }
    }

    private void refreshStatus() {
        boolean on = serviceEnabled();
        status.setText(on ? R.string.svc_on : R.string.svc_off);
        status.setTextColor(on ? getColor(R.color.accent) : 0xFFD9534F);
        statusHint.setText(on ? getString(R.string.usage) : getString(R.string.svc_hint_off));
    }

    private void refreshSlotButtons() {
        leftBtn.setText(getString(R.string.edit_left) + "  " + counts(true));
        rightBtn.setText(getString(R.string.edit_right) + "  " + counts(false));
    }

    /** 1 列なら「(4)」、2 列なら「(4 / 3)」と内側から順に出す。 */
    private String counts(boolean isLeft) {
        StringBuilder sb = new StringBuilder("(");
        for (int r = 0; r < cfg.rings; r++) {
            if (r > 0) sb.append(" / ");
            sb.append(cfg.slots(isLeft, r).size());
        }
        return sb.append(")").toString();
    }

    private void refreshPreview() {
        if (previewBox.getWidth() == 0) return;
        boolean isLeft = cfg.hasSlots(true) || !cfg.hasSlots(false);
        List<List<Slot>> rings = cfg.hasSlots(isLeft) ? cfg.ringSlots(isLeft) : demoRings();
        int total = 0;
        for (List<Slot> one : rings) total += one.size();
        float x = isLeft ? Ui.dp(this, 10) : previewBox.getWidth() - Ui.dp(this, 10);
        preview.begin(cfg, rings, isLeft, x, previewBox.getHeight() / 2f);
        preview.setForcedSelection(Math.min(1, total - 1));
    }

    /** まだ何も登録していないときに見本として出すもの。 */
    private List<List<Slot>> demoRings() {
        String[][] ids = {
                {"back", "home", "recents", "notifications", "settings"},
                {"quick_settings", "lock", "split"},
        };
        List<List<Slot>> out = new ArrayList<>();
        for (int r = 0; r < cfg.rings; r++) {
            List<Slot> one = new ArrayList<>();
            for (String id : ids[Math.min(r, ids.length - 1)]) {
                one.add(Slot.action(id, Actions.label(this, id)));
            }
            out.add(one);
        }
        return out;
    }

    private boolean serviceEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (flat == null) return false;
        String full = getPackageName() + "/" + OugistService.class.getName();
        String shortForm = getPackageName() + "/.OugistService";
        for (String part : flat.split(":")) {
            if (part.equalsIgnoreCase(full) || part.equalsIgnoreCase(shortForm)) return true;
        }
        return false;
    }

    // 設定画面の見本では震わせない。強さを選んだときだけ、その強さを 1 度だけ試す。
    @Override
    public void haptic(int level) {
    }

    private void sampleVibration(int level) {
        if (level <= 0) return;
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                v = vm == null ? null : vm.getDefaultVibrator();
            } else {
                v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int id = level == 1 ? VibrationEffect.EFFECT_TICK
                        : level == 2 ? VibrationEffect.EFFECT_CLICK
                        : VibrationEffect.EFFECT_HEAVY_CLICK;
                v.vibrate(VibrationEffect.createPredefined(id));
            } else {
                int ms = level == 1 ? 10 : level == 2 ? 18 : 28;
                int amp = level == 1 ? 60 : level == 2 ? 140 : 255;
                v.vibrate(VibrationEffect.createOneShot(ms, amp));
            }
        } catch (Throwable ignore) {
        }
    }
}
