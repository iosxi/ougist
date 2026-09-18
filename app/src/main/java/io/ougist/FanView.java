package io.ougist;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
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
    /** 内側の円から順につないだ 1 本の並び。 */
    private final List<Slot> flat = new ArrayList<>();
    private boolean left;

    private float downX, downY, fingerX, fingerY;
    private float cx, cy, radius, itemR, iconPx, glyphPx;   // radius は一番外の円
    private float[] angles = new float[0];
    private float[] px = new float[0];
    private float[] py = new float[0];
    private float[] grow = new float[0];

    // 円 (列) ごとの内訳。0 が内側。
    private int ringCount = 1;
    private final float[] ringR = new float[Config.MAX_RINGS];
    private final float[] ringStep = new float[Config.MAX_RINGS];
    private final int[] ringStart = new int[Config.MAX_RINGS];
    private final int[] ringN = new int[Config.MAX_RINGS];

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

    /** 指が端に触れて扇を開くとき。rings は内側から順、x, y は画面座標。 */
    void begin(Config cfg, List<List<Slot>> rings, boolean left, float x, float y) {
        this.cfg = cfg;
        this.left = left;
        this.forceSelect = -1;

        flat.clear();
        ringCount = Math.max(1, Math.min(rings.size(), Config.MAX_RINGS));
        for (int r = 0; r < Config.MAX_RINGS; r++) {
            ringStart[r] = 0;
            ringN[r] = 0;
        }
        for (int r = 0; r < ringCount; r++) {
            List<Slot> one = rings.get(r);
            ringStart[r] = flat.size();
            ringN[r] = one.size();
            flat.addAll(one);
        }

        downX = fingerX = x;
        downY = fingerY = y;
        selected = -1;
        laidOut = false;
        t0 = SystemClock.uptimeMillis();
        int n = flat.size();
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
        if (selected < 0 || selected >= flat.size()) return null;
        return flat.get(selected);
    }

    private int pick(float x, float y) {
        int n = flat.size();
        if (n == 0) return -1;
        float dx = left ? (x - cx) : (cx - x);
        float dy = y - cy;
        float dist = (float) Math.hypot(dx, dy);
        if (dist < ringR[0] * 0.38f) return -1;      // 中心付近に戻したら取り消し

        // まず、指の距離がどの円に近いかを決める
        int ring = -1;
        float bestGap = Float.MAX_VALUE;
        for (int r = 0; r < ringCount; r++) {
            if (ringN[r] == 0) continue;
            float gap = Math.abs(dist - ringR[r]);
            if (gap < bestGap) {
                bestGap = gap;
                ring = r;
            }
        }
        if (ring < 0) return -1;

        // その円の中で、角度が一番近いもの
        float a = (float) Math.atan2(dy, dx);
        int best = -1;
        float bestDiff = Float.MAX_VALUE;
        int from = ringStart[ring];
        int to = from + ringN[ring];
        for (int i = from; i < to; i++) {
            float diff = Math.abs(a - angles[i]);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        float step = ringN[ring] > 1 ? ringStep[ring] : (float) Math.toRadians(cfg.spanDeg);
        float allow = Math.max(step * 0.6f, (float) Math.toRadians(20));
        return bestDiff > allow ? -1 : best;
    }

    private void layoutFan() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        int n = flat.size();

        iconPx = cfg.iconDp * density;
        itemR = iconPx * 0.62f;
        glyphPx = iconPx * 0.56f;

        float half = (float) Math.toRadians(cfg.spanDeg) / 2f;
        float sinHalf = (float) Math.sin(Math.min(half, Math.PI / 2));
        float pad = itemR + 8 * density;
        float gap = itemR * 2f + 10 * density;          // 円と円の間隔
        float spread = (ringCount - 1) * gap;           // 内側から外側までの差

        float maxOuter = Math.min(w * 0.74f, (h / 2f - pad) / Math.max(sinHalf, 0.05f));
        float inner = cfg.radiusDp * density;
        float outer = inner + spread;
        if (outer > maxOuter) {                          // 画面に収まらなければ内へ寄せる
            outer = maxOuter;
            inner = outer - spread;
        }
        if (inner < itemR * 2f) {                        // それでも足りなければ内側を最小に
            inner = itemR * 2f;
            outer = inner + spread;
        }
        radius = outer;
        for (int r = 0; r < ringCount; r++) ringR[r] = inner + gap * r;

        float reach = radius * sinHalf;
        cx = downX;
        cy = clamp(downY, reach + pad, Math.max(reach + pad, h - reach - pad));

        int dir = left ? 1 : -1;
        for (int r = 0; r < ringCount; r++) {
            int cnt = ringN[r];
            ringStep[r] = cnt > 1 ? (2f * half) / (cnt - 1) : 2f * half;
            for (int i = 0; i < cnt; i++) {
                int k = ringStart[r] + i;
                float a = (cnt == 1) ? 0f : -half + ringStep[r] * i;
                angles[k] = a;
                px[k] = cx + dir * ringR[r] * (float) Math.cos(a);
                py[k] = cy + ringR[r] * (float) Math.sin(a);
            }
        }
        laidOut = true;
        selected = forceSelect >= 0 ? Math.min(forceSelect, n - 1) : pick(fingerX, fingerY);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (flat.isEmpty()) return;
        if (!laidOut) layoutFan();
        if (!laidOut) return;

        long now = SystemClock.uptimeMillis();
        float t = Math.min(1f, (now - t0) / (float) IN_MS);
        float p = 1f - (1f - t) * (1f - t) * (1f - t);   // 終わりに向けて緩む

        if (cfg.dimPct > 0) {
            c.drawColor(Color.argb((int) (cfg.dimPct * 2.55f * p), 0, 0, 0));
        }

        // 案内の弧。円 (列) ごとに 1 本
        float halfDeg = cfg.spanDeg / 2f;
        line.setColor(C_ARC);
        line.setAlpha((int) (0x33 * p));
        line.setStrokeWidth(1.5f * density);
        for (int r = 0; r < ringCount; r++) {
            if (ringN[r] == 0) continue;
            float rr = ringR[r] * p;
            rect.set(cx - rr, cy - rr, cx + rr, cy + rr);
            c.drawArc(rect, left ? -halfDeg : 180f - halfDeg, cfg.spanDeg, false, line);
        }

        // 中心の点。取り消し圏内にいるときだけはっきり光らせる
        fill.setColor(selected < 0 ? C_PIVOT_ON : C_PIVOT);
        fill.setAlpha((int) ((selected < 0 ? 0xCC : 0x55) * p));
        c.drawCircle(cx, cy, 5f * density * p, fill);

        boolean animating = t < 1f;
        int n = flat.size();
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
            drawItem(c, flat.get(i), x, y, s, grow[i], p);
        }

        if (cfg.showLabel && selected >= 0) {
            drawLabel(c, flat.get(selected),
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
