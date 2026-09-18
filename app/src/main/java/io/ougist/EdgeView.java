package io.ougist;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import java.util.Collections;

/**
 * 画面の左端または右端に置く、見えない細い帯。
 * ここに置いた指が内側へ動いたら扇を開き、そのまま指の動きを扇へ流す。
 */
final class EdgeView extends View {

    interface Host {
        float activatePx();

        boolean onGestureStart(boolean left, float x, float y);

        void onGestureMove(float x, float y);

        void onGestureEnd(boolean cancelled);
    }

    private final Host host;
    private final boolean left;

    private float downX, downY;
    private boolean active;
    private boolean given;   // 縦スクロールなどで扇を諦めた

    EdgeView(Context c, Host host, boolean left) {
        super(c);
        this.host = host;
        this.left = left;
        setBackgroundColor(Color.TRANSPARENT);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // ここはシステムの「戻る」ジェスチャに使わないでほしい、と申告する。
            // (端から 200dp ぶんまでしか聞いてもらえない決まりなので、効き方は端末次第)
            setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, w, h)));
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getRawX();
        float y = e.getRawY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                active = false;
                given = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!active && !given) {
                    float in = left ? (x - downX) : (downX - x);
                    float dy = Math.abs(y - downY);
                    float need = host.activatePx();
                    if (in >= need && in > dy * 0.7f) {
                        active = host.onGestureStart(left, downX, downY);
                        given = !active;
                    } else if (dy > need * 2.2f || in < -need) {
                        given = true;   // 縦になぞった / 外向きに出た
                    }
                }
                if (active) host.onGestureMove(x, y);
                return true;

            case MotionEvent.ACTION_UP:
                if (active) host.onGestureEnd(false);
                active = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (active) host.onGestureEnd(true);
                active = false;
                return true;

            default:
                return true;
        }
    }
}
