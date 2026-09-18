package io.ougist;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.List;

/**
 * ougist の本体。
 *
 * <p>アクセシビリティサービスであることを使うのは次の 2 点だけ。
 * <ul>
 *   <li>画面の端にオーバーレイ (TYPE_ACCESSIBILITY_OVERLAY) を置く。
 *       これなら「他のアプリの上に重ねて表示」の権限は要らない。</li>
 *   <li>戻る / ホーム / 履歴などの操作を実行する。</li>
 * </ul>
 * 画面の内容は読み取らないし、イベントの購読もしない。
 */
public class OugistService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        EdgeView.Host, FanView.Host {

    private static volatile boolean sRunning;

    public static boolean isRunning() {
        return sRunning;
    }

    private final Handler main = new Handler(Looper.getMainLooper());

    private WindowManager wm;
    private Config cfg;
    private Vibrator vib;

    private EdgeView edgeLeft, edgeRight;
    private FanView fan;
    private boolean fanShown;

    private CameraManager cam;
    private String torchId;
    private boolean torchOn;
    private CameraManager.TorchCallback torchCb;

    // ----------------------------------------------------------------- 生死

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sRunning = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        vib = Haptics.vibrator(this);
        cfg = Config.load(this);
        Config.prefs(this).registerOnSharedPreferenceChangeListener(this);
        rebuildEdges();
        warmIcons();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        teardown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        teardown();
        super.onDestroy();
    }

    private void teardown() {
        sRunning = false;
        main.removeCallbacks(applyPrefs);
        hideFan();
        removeEdges();
        try {
            Config.prefs(this).unregisterOnSharedPreferenceChangeListener(this);
        } catch (Throwable ignore) {
        }
        if (cam != null && torchCb != null) {
            try {
                cam.unregisterTorchCallback(torchCb);
            } catch (Throwable ignore) {
            }
            torchCb = null;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideFan();
        // 回転直後は新しい画面サイズがまだ返らないことがあるので一拍置く
        main.postDelayed(this::rebuildEdges, 200);
    }

    private final Runnable applyPrefs = () -> {
        cfg = Config.load(this);
        IconCache.clear();
        hideFan();
        rebuildEdges();
        warmIcons();
    };

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        // つまみを動かしている間は何度も書き込まれるので、落ち着いてから 1 度だけ反映する
        main.removeCallbacks(applyPrefs);
        main.postDelayed(applyPrefs, 250);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 何も購読していない
    }

    @Override
    public void onInterrupt() {
    }

    // ------------------------------------------------------------- 端の帯

    private void rebuildEdges() {
        removeEdges();
        if (wm == null || cfg == null) return;
        Rect b = displayBounds();
        int w = Math.round(cfg.triggerWidthDp * density());
        int top = b.height() * cfg.triggerTopPct / 100;
        int bottom = b.height() * cfg.triggerBottomPct / 100;
        int h = Math.max(Math.round(48 * density()), b.height() - top - bottom);
        if (cfg.leftEnabled && cfg.hasSlots(true)) edgeLeft = addEdge(true, w, h, top);
        if (cfg.rightEnabled && cfg.hasSlots(false)) edgeRight = addEdge(false, w, h, top);
    }

    private EdgeView addEdge(boolean left, int w, int h, int y) {
        EdgeView v = new EdgeView(this, this, left);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | (left ? Gravity.LEFT : Gravity.RIGHT);
        lp.y = y;
        lp.setTitle(left ? "ougist-edge-left" : "ougist-edge-right");
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        try {
            wm.addView(v, lp);
            return v;
        } catch (Throwable t) {
            return null;
        }
    }

    private void removeEdges() {
        edgeLeft = remove(edgeLeft);
        edgeRight = remove(edgeRight);
    }

    private <T extends android.view.View> T remove(T v) {
        if (v != null) {
            try {
                wm.removeViewImmediate(v);
            } catch (Throwable ignore) {
            }
        }
        return null;
    }

    // --------------------------------------------------------------- 扇

    @Override
    public float activatePx() {
        return cfg == null ? 48f : cfg.activateDp * density();
    }

    @Override
    public boolean onGestureStart(boolean left, float x, float y) {
        if (cfg == null || wm == null) return false;
        if (!cfg.hasSlots(left)) return false;
        List<List<Slot>> rings = cfg.ringSlots(left);
        if (fan == null) fan = new FanView(this, this);
        fan.begin(cfg, rings, left, x, y);
        if (!fanShown) {
            Rect b = displayBounds();
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    b.width(), b.height(),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.x = 0;
            lp.y = 0;
            lp.setTitle("ougist-fan");
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            try {
                wm.addView(fan, lp);
                fanShown = true;
            } catch (Throwable t) {
                return false;
            }
        }
        // 開いたことだけ伝えるので 1 段弱めに。強い設定のときに開始だけ弱いと不揃いなので、
        // 選んだ強さについていくようにしてある。
        haptic(cfg.haptic > 0 ? Math.max(1, cfg.haptic - 1) : 0);
        return true;
    }

    @Override
    public void onGestureMove(float x, float y) {
        if (fanShown && fan != null) fan.moveTo(x, y);
    }

    @Override
    public void onGestureEnd(boolean cancelled) {
        Slot chosen = (!cancelled && fan != null) ? fan.selectedSlot() : null;
        hideFan();
        if (chosen != null) run(chosen);
    }

    private void hideFan() {
        if (fanShown && fan != null) {
            try {
                wm.removeViewImmediate(fan);
            } catch (Throwable ignore) {
            }
        }
        fanShown = false;
    }

    // ------------------------------------------------------------- 実行

    private void run(Slot s) {
        if (s.type == Slot.TYPE_APP) {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(new ComponentName(s.pkg, s.cls));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                startActivity(i);
            } catch (Throwable t) {
                toast(getString(R.string.launch_failed));
            }
        } else {
            Actions.perform(this, s.actionId);
        }
    }

    void toggleTorch() {
        try {
            if (cam == null) cam = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            if (torchId == null) torchId = findTorch();
            if (torchId == null) {
                toast(getString(R.string.torch_failed));
                return;
            }
            if (torchCb == null) {
                torchCb = new CameraManager.TorchCallback() {
                    @Override
                    public void onTorchModeChanged(String id, boolean enabled) {
                        if (id.equals(torchId)) torchOn = enabled;
                    }

                    @Override
                    public void onTorchModeUnavailable(String id) {
                        if (id.equals(torchId)) torchOn = false;
                    }
                };
                cam.registerTorchCallback(torchCb, main);
            }
            cam.setTorchMode(torchId, !torchOn);
        } catch (Throwable t) {
            toast(getString(R.string.torch_failed));
        }
    }

    private String findTorch() throws Exception {
        String any = null;
        for (String id : cam.getCameraIdList()) {
            CameraCharacteristics ch = cam.getCameraCharacteristics(id);
            Boolean hasFlash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (!Boolean.TRUE.equals(hasFlash)) continue;
            Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
            if (any == null) any = id;
        }
        return any;
    }

    private void toast(final String msg) {
        main.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    // ------------------------------------------------------------- 触覚

    @Override
    public void haptic(int level) {
        Haptics.play(vib, level);
    }

    // --------------------------------------------------------------- 雑

    private void warmIcons() {
        if (cfg == null) return;
        final int app = Math.round(cfg.iconDp * density());
        final int glyph = Math.round(cfg.iconDp * density() * 0.56f);
        final Config c = cfg;
        new Thread(() -> {
            for (boolean side : new boolean[]{true, false}) {
                for (List<Slot> one : c.ringSlots(side)) {
                    IconCache.warm(OugistService.this, one, app, glyph);
                }
            }
        }, "ougist-icons").start();
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    @SuppressWarnings("deprecation")
    private Rect displayBounds() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return new Rect(wm.getCurrentWindowMetrics().getBounds());
        }
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        return new Rect(0, 0, dm.widthPixels, dm.heightPixels);
    }
}
