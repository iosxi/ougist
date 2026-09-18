package io.ougist;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 設定ひとまとめ。SharedPreferences に JSON 1 本で置く。 */
public final class Config {
    /** 1 つの円あたりに置ける数。 */
    public static final int MAX_SLOTS = 12;
    /** 円 (列) は内側と外側の 2 つまで。 */
    public static final int MAX_RINGS = 2;

    private static final String PREFS = "ougist";
    private static final String KEY = "config";

    /** 内側の円。 */
    public final List<Slot> left = new ArrayList<>();
    public final List<Slot> right = new ArrayList<>();
    /** 外側の円。列数が 2 のときだけ使う。 */
    public final List<Slot> leftOuter = new ArrayList<>();
    public final List<Slot> rightOuter = new ArrayList<>();

    public boolean leftEnabled = true;
    public boolean rightEnabled = true;

    public int rings = 1;             // 1..2    円 (列) の数
    public int triggerWidthDp = 14;   // 6..48   端の反応する帯の幅
    public int triggerTopPct = 0;     // 0..45   帯の上の余白 (画面高さに対する割合)
    public int triggerBottomPct = 0;  // 0..45   帯の下の余白
    public int activateDp = 18;       // 8..48   扇が開き始める移動量
    public int radiusDp = 132;        // 70..220 内側の円の半径
    public int iconDp = 46;           // 28..72  アイコンの大きさ
    public int spanDeg = 150;         // 60..180 扇の広がり
    public int dimPct = 30;           // 0..60   背景を暗くする度合い
    public boolean showLabel = true;
    public int haptic = 2;            // 0:なし 1:弱 … 6:最強 (Haptics.MAX)

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** ring は 0 が内側、1 が外側。 */
    public List<Slot> slots(boolean isLeft, int ring) {
        if (ring <= 0) return isLeft ? left : right;
        return isLeft ? leftOuter : rightOuter;
    }

    public List<Slot> slots(boolean isLeft) {
        return slots(isLeft, 0);
    }

    /** いま使う円ぶんの一覧。内側から順に並ぶ。 */
    public List<List<Slot>> ringSlots(boolean isLeft) {
        List<List<Slot>> out = new ArrayList<>(rings);
        for (int r = 0; r < rings; r++) out.add(slots(isLeft, r));
        return out;
    }

    /** その側に 1 つでも登録があるか。 */
    public boolean hasSlots(boolean isLeft) {
        for (int r = 0; r < rings; r++) {
            if (!slots(isLeft, r).isEmpty()) return true;
        }
        return false;
    }

    public static Config load(Context c) {
        Config cfg = new Config();
        String raw = prefs(c).getString(KEY, null);
        if (raw == null) {
            cfg.fillDefaults(c);
            return cfg;
        }
        try {
            JSONObject o = new JSONObject(raw);
            readSlots(o.optJSONArray("left"), cfg.left);
            readSlots(o.optJSONArray("right"), cfg.right);
            readSlots(o.optJSONArray("left2"), cfg.leftOuter);
            readSlots(o.optJSONArray("right2"), cfg.rightOuter);
            cfg.leftEnabled = o.optBoolean("le", true);
            cfg.rightEnabled = o.optBoolean("re", true);
            cfg.rings = clamp(o.optInt("rg", cfg.rings), 1, MAX_RINGS);
            cfg.triggerWidthDp = clamp(o.optInt("tw", cfg.triggerWidthDp), 6, 48);
            int legacy = clamp(o.optInt("tm", 0), 0, 45);   // 以前の「上下の余白」
            cfg.triggerTopPct = clamp(o.optInt("tt", legacy), 0, 45);
            cfg.triggerBottomPct = clamp(o.optInt("tb", legacy), 0, 45);
            cfg.activateDp = clamp(o.optInt("ac", cfg.activateDp), 8, 48);
            cfg.radiusDp = clamp(o.optInt("r", cfg.radiusDp), 70, 220);
            cfg.iconDp = clamp(o.optInt("ic", cfg.iconDp), 28, 72);
            cfg.spanDeg = clamp(o.optInt("sp", cfg.spanDeg), 60, 180);
            cfg.dimPct = clamp(o.optInt("dm", cfg.dimPct), 0, 60);
            cfg.showLabel = o.optBoolean("lb", true);
            cfg.haptic = clamp(o.optInt("hp", cfg.haptic), 0, Haptics.MAX);
        } catch (Exception ignore) {
        }
        return cfg;
    }

    public void save(Context c) {
        JSONObject o = new JSONObject();
        try {
            o.put("left", writeSlots(left));
            o.put("right", writeSlots(right));
            o.put("left2", writeSlots(leftOuter));
            o.put("right2", writeSlots(rightOuter));
            o.put("le", leftEnabled);
            o.put("re", rightEnabled);
            o.put("rg", rings);
            o.put("tw", triggerWidthDp);
            o.put("tt", triggerTopPct);
            o.put("tb", triggerBottomPct);
            o.put("ac", activateDp);
            o.put("r", radiusDp);
            o.put("ic", iconDp);
            o.put("sp", spanDeg);
            o.put("dm", dimPct);
            o.put("lb", showLabel);
            o.put("hp", haptic);
        } catch (Exception ignore) {
        }
        prefs(c).edit().putString(KEY, o.toString()).apply();
    }

    /** 初回起動のとき。入れてすぐ試せるように、よく使う操作を並べておく。 */
    private void fillDefaults(Context c) {
        String[] l = {"back", "home", "recents", "notifications"};
        String[] r = {"back", "home", "recents", "quick_settings"};
        for (String id : l) left.add(Slot.action(id, Actions.label(c, id)));
        for (String id : r) right.add(Slot.action(id, Actions.label(c, id)));
    }

    private static void readSlots(JSONArray a, List<Slot> out) {
        out.clear();
        if (a == null) return;
        for (int i = 0; i < a.length() && out.size() < MAX_SLOTS; i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Slot s = Slot.fromJson(o);
            if (s != null) out.add(s);
        }
    }

    private static JSONArray writeSlots(List<Slot> in) {
        JSONArray a = new JSONArray();
        for (Slot s : in) a.put(s.toJson());
        return a;
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
