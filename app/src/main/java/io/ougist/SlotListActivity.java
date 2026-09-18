package io.ougist;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** 片側ぶんのアイコン一覧。並べ替えと、アプリ / 操作の割り当て。 */
public class SlotListActivity extends Activity {

    private static final int REQ_APP = 1;

    private Config cfg;
    private boolean left;
    private int ring;                // 0 が内側、1 が外側
    private List<Slot> slots;
    private Adapter adapter;
    private TextView empty;
    private TextView head;
    private final Button[] ringBtns = new Button[Config.MAX_RINGS];
    private int pendingIndex = -1;   // -1 は新規追加

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        left = getIntent().getBooleanExtra("left", true);
        cfg = Config.load(this);
        ring = 0;
        slots = cfg.slots(left, ring);
        setTitle(getString(R.string.slot_title_fmt,
                getString(left ? R.string.side_left : R.string.side_right)));

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(getColor(R.color.bg));
        Ui.fitSystemBars(root, 12, 12);

        head = Ui.title(this, "");
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        head.setPadding(0, 0, 0, Ui.dp(this, 6));
        root.addView(head);

        // 2 列のときだけ、内側 / 外側を切り替えるつまみを出す
        if (cfg.rings > 1) {
            LinearLayout ringRow = Ui.row(this);
            ringRow.setPadding(0, 0, 0, Ui.dp(this, 8));
            String[] names = {getString(R.string.ring_inner), getString(R.string.ring_outer)};
            for (int i = 0; i < Config.MAX_RINGS; i++) {
                final int which = i;
                Button rb = Ui.button(this, names[i], v -> switchRing(which));
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                rlp.rightMargin = i < Config.MAX_RINGS - 1 ? Ui.dp(this, 6) : 0;
                ringRow.addView(rb, rlp);
                ringBtns[i] = rb;
            }
            root.addView(ringRow);
        }

        empty = Ui.body(this, getString(R.string.empty_slots));
        empty.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        root.addView(empty);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(Ui.dp(this, 6));
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> choose(pos));
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button add = Ui.button(this, getString(R.string.add_slot), v -> {
            if (slots.size() >= Config.MAX_SLOTS) {
                Toast.makeText(this, R.string.max_slots, Toast.LENGTH_SHORT).show();
                return;
            }
            choose(-1);
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = Ui.dp(this, 8);
        root.addView(add, alp);

        setContentView(root);
        refresh();
    }

    private void switchRing(int which) {
        if (which == ring) return;
        ring = which;
        slots = cfg.slots(left, ring);
        pendingIndex = -1;
        refresh();
    }

    private void refresh() {
        String side = getString(left ? R.string.side_left : R.string.side_right);
        if (cfg.rings > 1) {
            String where = getString(ring == 0 ? R.string.ring_inner : R.string.ring_outer);
            head.setText(getString(R.string.slot_title_fmt,
                    getString(R.string.slot_ring_fmt, side, where)));
            for (int i = 0; i < ringBtns.length; i++) {
                if (ringBtns[i] == null) continue;
                boolean on = i == ring;
                ringBtns[i].setBackground(Ui.pill(this,
                        on ? getColor(R.color.accent) : Ui.alpha(getColor(R.color.text_sub), 0.16f), 10));
                ringBtns[i].setTextColor(on ? android.graphics.Color.WHITE : getColor(R.color.text));
            }
        } else {
            head.setText(getString(R.string.slot_title_fmt, side));
        }
        empty.setVisibility(slots.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void save() {
        cfg.save(this);
        refresh();
    }

    /** 1 つのコマに何を入れるか選ぶ。 */
    private void choose(final int index) {
        pendingIndex = index;
        String[] items = index < 0
                ? new String[]{getString(R.string.pick_app), getString(R.string.pick_action)}
                : new String[]{getString(R.string.pick_app), getString(R.string.pick_action),
                getString(R.string.pick_delete)};
        new AlertDialog.Builder(this)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        startActivityForResult(new Intent(this, AppPickerActivity.class), REQ_APP);
                    } else if (which == 1) {
                        pickAction();
                    } else {
                        slots.remove(index);
                        save();
                    }
                })
                .show();
    }

    private void pickAction() {
        final List<Actions.Def> defs = Actions.available();
        BaseAdapter a = new BaseAdapter() {
            @Override
            public int getCount() {
                return defs.size();
            }

            @Override
            public Object getItem(int i) {
                return defs.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View convert, ViewGroup parent) {
                LinearLayout row = Ui.row(SlotListActivity.this);
                int p = Ui.dp(SlotListActivity.this, 10);
                row.setPadding(p + p, p, p, p);
                ImageView iv = new ImageView(SlotListActivity.this);
                iv.setImageResource(defs.get(i).iconRes);
                iv.setImageTintList(android.content.res.ColorStateList.valueOf(
                        getColor(R.color.text)));
                int s = Ui.dp(SlotListActivity.this, 24);
                row.addView(iv, new LinearLayout.LayoutParams(s, s));
                TextView t = Ui.title(SlotListActivity.this, getString(defs.get(i).labelRes));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = Ui.dp(SlotListActivity.this, 14);
                row.addView(t, lp);
                return row;
            }
        };
        new AlertDialog.Builder(this)
                .setAdapter(a, (d, which) -> {
                    Actions.Def def = defs.get(which);
                    put(Slot.action(def.id, getString(def.labelRes)));
                })
                .show();
    }

    private void put(Slot s) {
        if (pendingIndex >= 0 && pendingIndex < slots.size()) {
            slots.set(pendingIndex, s);
        } else if (slots.size() < Config.MAX_SLOTS) {
            slots.add(s);
        }
        pendingIndex = -1;
        save();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_APP && res == RESULT_OK && data != null) {
            put(Slot.app(data.getStringExtra("pkg"),
                    data.getStringExtra("cls"),
                    data.getStringExtra("label")));
        }
    }

    private void move(int from, int to) {
        if (to < 0 || to >= slots.size()) return;
        Slot s = slots.remove(from);
        slots.add(to, s);
        save();
    }

    // -------------------------------------------------------------- 一覧

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return slots.size();
        }

        @Override
        public Object getItem(int i) {
            return slots.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(final int i, View convert, ViewGroup parent) {
            final Slot s = slots.get(i);
            LinearLayout row = Ui.row(SlotListActivity.this);
            row.setBackground(Ui.pill(SlotListActivity.this, getColor(R.color.card), 14));
            int p = Ui.dp(SlotListActivity.this, 10);
            row.setPadding(p, p, p, p);

            ImageView iv = new ImageView(SlotListActivity.this);
            int size = Ui.dp(SlotListActivity.this, 36);
            Bitmap bmp = IconCache.get(SlotListActivity.this, s, size);
            if (bmp == null) bmp = IconCache.fallback(SlotListActivity.this, size);
            iv.setImageBitmap(bmp);
            if (s.type == Slot.TYPE_ACTION) {
                iv.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.text)));
                iv.setPadding(0, 0, 0, 0);
            }
            row.addView(iv, new LinearLayout.LayoutParams(size, size));

            LinearLayout texts = Ui.column(SlotListActivity.this);
            TextView name = Ui.title(SlotListActivity.this,
                    s.label == null || s.label.isEmpty() ? getString(R.string.missing) : s.label);
            TextView kind = Ui.body(SlotListActivity.this,
                    getString(s.type == Slot.TYPE_APP ? R.string.type_app : R.string.type_action));
            kind.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            texts.addView(name);
            texts.addView(kind);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = Ui.dp(SlotListActivity.this, 12);
            row.addView(texts, tlp);

            row.addView(iconButton("▲", v -> move(i, i - 1)));
            row.addView(iconButton("▼", v -> move(i, i + 1)));
            row.addView(iconButton("✕", v -> {
                slots.remove(i);
                save();
            }));
            return row;
        }

        private View iconButton(String label, View.OnClickListener l) {
            TextView t = new TextView(SlotListActivity.this);
            t.setText(label);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            t.setTextColor(getColor(R.color.text_sub));
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(l);
            t.setFocusable(false);
            t.setBackground(Ui.pill(SlotListActivity.this,
                    Ui.alpha(getColor(R.color.text_sub), 0.12f), 8));
            int s = Ui.dp(SlotListActivity.this, 38);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.leftMargin = Ui.dp(SlotListActivity.this, 6);
            t.setLayoutParams(lp);
            return t;
        }
    }
}
