package io.ougist;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.util.List;

/** 画面いっぱいの板。扇そのものを描き、指の位置からどれを選んでいるかを決める。 */
final class FanView extends View {

    interface Host {
        void haptic(int level);
    }

    private static final int C_ITEM = 0xE61B1E26;
    private static final int C_ITEM_SEL = 0xF2263258;
    private static final int C_RING = 0xFF8AA9FF;
    private static final int C_ARC = 0x33FFFFFF;
    private static final int C_PIVOT = 0x55FFFFFF;
    private static final int C_PIVOT_ON = 0xCCFFFFFF;
    private static final int C_LINE = 0x558AA9FF;
    private static final int C_LABEL_BG = 0xF21B1E26;

    private static final long IN_MS = 150;

    private final Host host;
    private final float density;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private Config cfg;
    private List<Slot> slots;
    private boolean left;

    private float downX, downY, fingerX, fingerY;
    private float cx, cy, radius, itemR, iconPx, glyphPx;
    private float[] angles = new float[0];
    private float[] px = new float[0];
    private float[] py = new float[0];
    private float[] grow = new float[0];
    private int selected = -1;
    private int forceSelect = -1;   // 設定画面の見本で使う
    private long t0;
    private boolean laidOut;

    FanView(Context c, Host host) {
        super(c);
        this.host = host;
        this.density = c.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        text.setTextSize(13f * c.getResources().getDisplayMetrics().scaledDensity);
        text.setColor(Color.WHITE);
        setWillNotDraw(false);
    }

    /** 指が端に触れて扇を開くとき。x, y は画面座標。 */
    void begin(Config cfg, List<Slot> slots, boolean left, float x, float y) {
        this.cfg = cfg;
        this.slots = slots;
        this.left = left;
        this.forceSelect = -1;
        downX = fingerX = x;
        downY = fingerY = y;
        selected = -1;
        laidOut = false;
        t0 = SystemClock.uptimeMillis();
        int n = slots.size();
        if (angles.length != n) {
            angles = new float[n];
            px = new float[n];
            py = new float[n];
            grow = new float[n];
        }
        for (int i = 0; i < n; i++) {
            grow[i] = 0f;
        }
        invalidate();
    }

    /** 指が動いたとき。選んでいるものが変われば軽く震わせる。 */
    void moveTo(float x, float y) {
        fingerX = x;
        fingerY = y;
        if (laidOut) {
            int now = pick(x, y);
            if (now != selected) {
                selected = now;
                if (now >= 0) host.haptic(cfg.haptic);
            }
        }
        invalidate();
    }

    /** 設定画面の見本で、指なしに 1 つ選んだ状態を見せる。 */
    void setForcedSelection(int index) {
        forceSelect = index;
        laidOut = false;
        invalidate();
    }

    Slot selectedSlot() {
        if (slots == null || selected < 0 || selected >= slots.size()) return null;
        return slots.get(selected);
    }

    private int pick(float x, float y) {
        int n = slots.size();
        if (n == 0) return -1;
        float dx = left ? (x - cx) : (cx - x);
        float dy = y - cy;
        float dist = (float) Math.hypot(dx, dy);
        if (dist < radius * 0.38f) return -1;        // 中心付近に戻したら取り消し
        float a = (float) Math.atan2(dy, dx);
        int best = -1;
        float bestDiff = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float diff = Math.abs(a - angles[i]);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        float step = n > 1 ? Math.abs(angles[1] - angles[0]) : (float) Math.toRadians(cfg.spanDeg);
        float allow = Math.max(step * 0.6f, (float) Math.toRadians(20));
        return bestDiff > allow ? -1 : best;
    }

    private void layoutFan() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        int n = slots.size();

        iconPx = cfg.iconDp * density;
        itemR = iconPx * 0.62f;
        glyphPx = iconPx * 0.56f;

        float half = (float) Math.toRadians(cfg.spanDeg) / 2f;
        float sinHalf = (float) Math.sin(Math.min(half, Math.PI / 2));
        float pad = itemR + 8 * density;

        radius = Math.min(cfg.radiusDp * density, w * 0.74f);
        float maxR = (h / 2f - pad) / Math.max(sinHalf, 0.05f);
        radius = Math.max(itemR * 2f, Math.min(radius, maxR));

        float reach = radius * sinHalf;
        cx = downX;
        cy = clamp(downY, reach + pad, Math.max(reach + pad, h - reach - pad));

        int dir = left ? 1 : -1;
        for (int i = 0; i < n; i++) {
            float a = (n == 1) ? 0f : -half + (2f * half) * i / (n - 1);
            angles[i] = a;
            px[i] = cx + dir * radius * (float) Math.cos(a);
            py[i] = cy + radius * (float) Math.sin(a);
        }
        laidOut = true;
        selected = forceSelect >= 0 ? Math.min(forceSelect, n - 1) : pick(fingerX, fingerY);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (slots == null || slots.isEmpty()) return;
        if (!laidOut) layoutFan();
        if (!laidOut) return;

        long now = SystemClock.uptimeMillis();
        float t = Math.min(1f, (now - t0) / (float) IN_MS);
        float p = 1f - (1f - t) * (1f - t) * (1f - t);   // 終わりに向けて緩む

        if (cfg.dimPct > 0) {
            c.drawColor(Color.argb((int) (cfg.dimPct * 2.55f * p), 0, 0, 0));
        }

        // 案内の弧
        float r = radius * p;
        float halfDeg = cfg.spanDeg / 2f;
        rect.set(cx - r, cy - r, cx + r, cy + r);
        line.setColor(C_ARC);
        line.setAlpha((int) (0x33 * p));
        line.setStrokeWidth(1.5f * density);
        c.drawArc(rect, left ? -halfDeg : 180f - halfDeg, cfg.spanDeg, false, line);

        // 中心の点。取り消し圏内にいるときだけはっきり光らせる
        fill.setColor(selected < 0 ? C_PIVOT_ON : C_PIVOT);
        fill.setAlpha((int) ((selected < 0 ? 0xCC : 0x55) * p));
        c.drawCircle(cx, cy, 5f * density * p, fill);

        boolean animating = t < 1f;
        int n = slots.size();
        for (int i = 0; i < n; i++) {
            float target = (i == selected) ? 1f : 0f;
            if (Math.abs(grow[i] - target) > 0.004f) {
                grow[i] += (target - grow[i]) * 0.4f;
                animating = true;
            } else {
                grow[i] = target;
            }
        }

        if (selected >= 0) {
            line.setColor(C_LINE);
            line.setAlpha((int) (0x55 * p));
            line.setStrokeWidth(2f * density);
            c.drawLine(cx, cy, cx + (px[selected] - cx) * p, cy + (py[selected] - cy) * p, line);
        }

        for (int i = 0; i < n; i++) {
            float x = cx + (px[i] - cx) * p;
            float y = cy + (py[i] - cy) * p;
            float s = (0.82f + 0.18f * p) * (1f + 0.16f * grow[i]);
            drawItem(c, slots.get(i), x, y, s, grow[i], p);
        }

        if (cfg.showLabel && selected >= 0) {
            drawLabel(c, slots.get(selected),
                    cx + (px[selected] - cx) * p,
                    cy + (py[selected] - cy) * p,
                    itemR * (1f + 0.16f * grow[selected]));
        }

        if (animating) postInvalidateOnAnimation();
    }

    private void drawItem(Canvas c, Slot s, float x, float y, float scale, float sel, float p) {
        float rr = itemR * scale;
        int bg = blend(C_ITEM, C_ITEM_SEL, sel);
        fill.setColor(bg);
        fill.setAlpha((int) (Color.alpha(bg) * p));
        c.drawCircle(x, y, rr, fill);

        if (sel > 0.01f) {
            line.setColor(C_RING);
            line.setAlpha((int) (255 * sel * p));
            line.setStrokeWidth(2.5f * density);
            c.drawCircle(x, y, rr + 1.6f * density, line);
        }

        Bitmap bmp;
        float size;
        if (s.type == Slot.TYPE_APP) {
            size = iconPx * scale;
            bmp = IconCache.get(getContext(), s, Math.round(iconPx));
            if (bmp == null) {
                size = glyphPx * scale;
                bmp = IconCache.fallback(getContext(), Math.round(glyphPx));
            }
        } else {
            size = glyphPx * scale;
            bmp = IconCache.get(getContext(), s, Math.round(glyphPx));
        }
        if (bmp == null) return;
        rect.set(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f);
        fill.setColor(Color.WHITE);
        fill.setAlpha((int) (255 * p));
        c.drawBitmap(bmp, null, rect, fill);
    }

    private void drawLabel(Canvas c, Slot s, float x, float y, float rr) {
        String label = (s.label == null || s.label.length() == 0)
                ? getContext().getString(R.string.missing) : s.label;
        float padH = 9f * density;
        float w = text.measureText(label) + padH * 2f;
        float h = 26f * density;
        float gap = 9f * density;
        float edge = 6f * density;

        float lx = left ? x + rr + gap : x - rr - gap - w;
        if (lx + w > getWidth() - edge) lx = x - rr - gap - w;
        if (lx < edge) lx = x + rr + gap;
        lx = clamp(lx, edge, Math.max(edge, getWidth() - w - edge));
        float ly = clamp(y - h / 2f, edge, Math.max(edge, getHeight() - h - edge));

        rect.set(lx, ly, lx + w, ly + h);
        fill.setColor(C_LABEL_BG);
        fill.setAlpha(Color.alpha(C_LABEL_BG));
        c.drawRoundRect(rect, h / 2f, h / 2f, fill);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(label, lx + padH, ly + h / 2f - (fm.ascent + fm.descent) / 2f, text);
    }

    private static int blend(int a, int b, float f) {
        f = f < 0f ? 0f : (f > 1f ? 1f : f);
        int fi = (int) (f * 256f);
        return Color.argb(
                mix(Color.alpha(a), Color.alpha(b), fi),
                mix(Color.red(a), Color.red(b), fi),
                mix(Color.green(a), Color.green(b), fi),
                mix(Color.blue(a), Color.blue(b), fi));
    }

    private static int mix(int a, int b, int fi) {
        return (a * (256 - fi) + b * fi) / 256;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
