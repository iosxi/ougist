package io.ougist;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.LruCache;

import java.util.List;

/** アイコンを描く大きさに合わせてビットマップにしておく置き場。 */
public final class IconCache {

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    public static void clear() {
        CACHE.evictAll();
    }

    /** 扇や一覧に出すアイコン。size は実際に描く一辺のピクセル数。 */
    public static Bitmap get(Context ctx, Slot s, int size) {
        if (size <= 0) return null;
        String k = s.key() + "#" + size;
        Bitmap b = CACHE.get(k);
        if (b != null) return b;
        Drawable d = load(ctx, s);
        if (d == null) return null;
        b = render(d, size, s.type == Slot.TYPE_APP);
        if (b != null) CACHE.put(k, b);
        return b;
    }

    /** 使うアイコンを先に作っておく (ジェスチャ中に読み込みで詰まらせない)。 */
    public static void warm(final Context ctx, final List<Slot> slots, final int appSize, final int glyphSize) {
        for (Slot s : slots) {
            try {
                get(ctx, s, s.type == Slot.TYPE_APP ? appSize : glyphSize);
            } catch (Throwable ignore) {
            }
        }
    }

    private static Drawable load(Context ctx, Slot s) {
        try {
            if (s.type == Slot.TYPE_APP) {
                PackageManager pm = ctx.getPackageManager();
                return pm.getActivityInfo(new ComponentName(s.pkg, s.cls), 0).loadIcon(pm);
            }
            Actions.Def d = Actions.find(s.actionId);
            if (d == null) return null;
            Drawable dr = ctx.getDrawable(d.iconRes);
            if (dr != null) {
                dr = dr.mutate();
                dr.setTint(Color.WHITE);
            }
            return dr;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * アプリのアイコンは円に合わせて切り、操作のアイコンはそのまま描く。
     *
     * <p>AdaptiveIconDrawable は draw() の中で自分の層を 1.5 倍に広げてから端末の形で抜く
     * (AOSP の updateLayerBoundsInternal が DEFAULT_VIEW_PORT_SCALE = 1/1.5 で子の枠を広げる)。
     * ここで重ねて 1.5 倍に置くと 2.25 倍になり、アイコンのフチが落ちて何のアプリか読み取れない。
     * そこで層を自分で取り出し、決まりどおりの 1.5 倍だけ広げて丸く抜く。ランチャーと同じ見え方になる。
     */
    private static Bitmap render(Drawable d, int size, boolean maskToCircle) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        boolean adaptive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && d instanceof AdaptiveIconDrawable;
        if (maskToCircle && adaptive) {
            AdaptiveIconDrawable ad = (AdaptiveIconDrawable) d;
            // 108 の画用紙のうち中央 72 だけが見える、という決まりに合わせて 1.5 倍に置く
            int full = Math.round(size * 108f / 72f);
            int off = (size - full) / 2;
            Bitmap tmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas tc = new Canvas(tmp);
            Drawable bg = ad.getBackground();
            Drawable fg = ad.getForeground();
            if (bg == null && fg == null) {          // 層を持たない作りなら丸ごと置く
                d.setBounds(0, 0, size, size);
                d.draw(tc);
            } else {
                if (bg != null) {
                    bg.setBounds(off, off, off + full, off + full);
                    bg.draw(tc);
                }
                if (fg != null) {
                    fg.setBounds(off, off, off + full, off + full);
                    fg.draw(tc);
                }
            }
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.BLACK);
            c.drawCircle(size / 2f, size / 2f, size / 2f, p);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            c.drawBitmap(tmp, 0, 0, p);
            tmp.recycle();
        } else if (maskToCircle) {
            // 昔ながらのアイコンは自前で形を持っているので、少しだけ縮めてそのまま置く
            int inset = Math.round(size * 0.04f);
            d.setBounds(inset, inset, size - inset, size - inset);
            d.draw(c);
        } else {
            d.setBounds(0, 0, size, size);
            d.draw(c);
        }
        return bmp;
    }

    /** 見つからないアプリ用の代わりのアイコン。 */
    public static Bitmap fallback(Context ctx, int size) {
        String k = "@@fallback#" + size;
        Bitmap b = CACHE.get(k);
        if (b != null) return b;
        Drawable d = ctx.getDrawable(R.drawable.ic_app);
        if (d == null) return null;
        d = d.mutate();
        d.setTint(Color.WHITE);
        b = render(d, size, false);
        CACHE.put(k, b);
        return b;
    }

    private IconCache() {
    }
}
