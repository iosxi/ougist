package io.ougist;

import org.json.JSONObject;

/** 扇のひとコマ。「アプリ」か「端末の操作」のどちらか。 */
public final class Slot {
    public static final int TYPE_APP = 0;
    public static final int TYPE_ACTION = 1;

    public int type;
    public String pkg;
    public String cls;
    public String actionId;
    public String label = "";

    public static Slot app(String pkg, String cls, String label) {
        Slot s = new Slot();
        s.type = TYPE_APP;
        s.pkg = pkg;
        s.cls = cls;
        s.label = label == null ? "" : label;
        return s;
    }

    public static Slot action(String id, String label) {
        Slot s = new Slot();
        s.type = TYPE_ACTION;
        s.actionId = id;
        s.label = label == null ? "" : label;
        return s;
    }

    /** アイコン画像のキャッシュキー。 */
    public String key() {
        return type == TYPE_APP ? pkg + "/" + cls : "@" + actionId;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("t", type);
            o.put("l", label);
            if (type == TYPE_APP) {
                o.put("p", pkg);
                o.put("c", cls);
            } else {
                o.put("a", actionId);
            }
        } catch (Exception ignore) {
        }
        return o;
    }

    public static Slot fromJson(JSONObject o) {
        Slot s = new Slot();
        s.type = o.optInt("t", TYPE_APP);
        s.label = o.optString("l", "");
        s.pkg = o.optString("p", null);
        s.cls = o.optString("c", null);
        s.actionId = o.optString("a", null);
        if (s.type == TYPE_APP && (s.pkg == null || s.cls == null)) return null;
        if (s.type == TYPE_ACTION && s.actionId == null) return null;
        return s;
    }
}
