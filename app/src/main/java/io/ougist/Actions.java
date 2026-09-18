package io.ougist;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/** アイコンに登録できる「端末の操作」の一覧と実行。 */
public final class Actions {

    public static final class Def {
        public final String id;
        public final int labelRes;
        public final int iconRes;
        final int minSdk;
        final int globalAction; // 0 ならグローバル操作ではない

        Def(String id, int labelRes, int iconRes, int minSdk, int globalAction) {
            this.id = id;
            this.labelRes = labelRes;
            this.iconRes = iconRes;
            this.minSdk = minSdk;
            this.globalAction = globalAction;
        }
    }

    private static final int A_NONE = 0;

    private static final Def[] ALL = {
            new Def("back", R.string.a_back, R.drawable.ic_back, 28,
                    AccessibilityService.GLOBAL_ACTION_BACK),
            new Def("home", R.string.a_home, R.drawable.ic_home, 28,
                    AccessibilityService.GLOBAL_ACTION_HOME),
            new Def("recents", R.string.a_recents, R.drawable.ic_recents, 28,
                    AccessibilityService.GLOBAL_ACTION_RECENTS),
            new Def("notifications", R.string.a_notifications, R.drawable.ic_notifications, 28,
                    AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS),
            new Def("quick_settings", R.string.a_quick_settings, R.drawable.ic_quick_settings, 28,
                    AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS),
            new Def("dismiss", R.string.a_dismiss, R.drawable.ic_dismiss, 31,
                    AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE),
            new Def("lock", R.string.a_lock, R.drawable.ic_lock, 28,
                    AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN),
            new Def("power", R.string.a_power, R.drawable.ic_power, 28,
                    AccessibilityService.GLOBAL_ACTION_POWER_DIALOG),
            new Def("screenshot", R.string.a_screenshot, R.drawable.ic_screenshot, 30,
                    AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT),
            new Def("split", R.string.a_split, R.drawable.ic_split, 28,
                    AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN),
            new Def("media", R.string.a_media, R.drawable.ic_media, 33,
                    AccessibilityService.GLOBAL_ACTION_KEYCODE_HEADSETHOOK),
            new Def("torch", R.string.a_torch, R.drawable.ic_torch, 28, A_NONE),
            new Def("settings", R.string.a_settings, R.drawable.ic_settings, 28, A_NONE),
    };

    /** この端末で使えるものだけ。 */
    public static List<Def> available() {
        List<Def> out = new ArrayList<>(ALL.length);
        for (Def d : ALL) {
            if (Build.VERSION.SDK_INT >= d.minSdk) out.add(d);
        }
        return out;
    }

    public static Def find(String id) {
        if (id == null) return null;
        for (Def d : ALL) {
            if (d.id.equals(id)) return d;
        }
        return null;
    }

    public static String label(Context c, String id) {
        Def d = find(id);
        return d == null ? c.getString(R.string.missing) : c.getString(d.labelRes);
    }

    public static void perform(OugistService svc, String id) {
        Def d = find(id);
        if (d == null || Build.VERSION.SDK_INT < d.minSdk) return;
        if (d.globalAction != A_NONE) {
            try {
                svc.performGlobalAction(d.globalAction);
            } catch (Throwable ignore) {
            }
            return;
        }
        if ("torch".equals(id)) {
            svc.toggleTorch();
        } else if ("settings".equals(id)) {
            Intent i = new Intent(svc, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                svc.startActivity(i);
            } catch (Throwable ignore) {
            }
        }
    }

    private Actions() {
    }
}
