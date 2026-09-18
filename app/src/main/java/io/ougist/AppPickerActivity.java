package io.ougist;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** ランチャーに出るアプリの一覧から 1 つ選ぶ。 */
public class AppPickerActivity extends Activity {

    static final class Entry {
        String label, pkg, cls;
        Bitmap icon;
    }

    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> shown = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Adapter adapter;
    private TextView state;
    private int iconPx;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        iconPx = Ui.dp(this, 40);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(getColor(R.color.bg));
        Ui.fitSystemBars(root, 12, 12);

        TextView title = Ui.title(this, getString(R.string.pick_app));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setPadding(0, 0, 0, Ui.dp(this, 10));
        root.addView(title);

        EditText search = new EditText(this);
        search.setHint(R.string.search_app);
        search.setSingleLine(true);
        search.setTextColor(getColor(R.color.text));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        root.addView(search);

        state = Ui.body(this, getString(R.string.loading));
        state.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        root.addView(state);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(Ui.dp(this, 2));
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Entry e = shown.get(pos);
            setResult(RESULT_OK, new Intent()
                    .putExtra("pkg", e.pkg)
                    .putExtra("cls", e.cls)
                    .putExtra("label", e.label));
            finish();
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        new Thread(this::loadApps, "ougist-apps").start();
    }

    private void loadApps() {
        final List<Entry> found = new ArrayList<>();
        try {
            PackageManager pm = getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(i, 0);
            for (ResolveInfo ri : infos) {
                if (ri.activityInfo == null) continue;
                Entry e = new Entry();
                e.pkg = ri.activityInfo.packageName;
                e.cls = ri.activityInfo.name;
                CharSequence l = ri.loadLabel(pm);
                e.label = l == null ? e.pkg : l.toString();
                found.add(e);
            }
            final Collator col = Collator.getInstance(Locale.getDefault());
            Collections.sort(found, (a, b) -> col.compare(a.label, b.label));
            // 先に絵を作っておくと、一覧を指でなぞっても引っかからない
            for (Entry e : found) {
                Slot s = Slot.app(e.pkg, e.cls, e.label);
                e.icon = IconCache.get(this, s, iconPx);
            }
        } catch (Throwable ignore) {
        }
        main.post(() -> {
            all.clear();
            all.addAll(found);
            state.setVisibility(View.GONE);
            filter("");
        });
    }

    private void filter(String q) {
        shown.clear();
        String needle = q.trim().toLowerCase(Locale.getDefault());
        for (Entry e : all) {
            if (needle.isEmpty()
                    || e.label.toLowerCase(Locale.getDefault()).contains(needle)
                    || e.pkg.toLowerCase(Locale.getDefault()).contains(needle)) {
                shown.add(e);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int i) {
            return shown.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Entry e = shown.get(i);
            LinearLayout row;
            if (convert instanceof LinearLayout) {
                row = (LinearLayout) convert;
            } else {
                row = Ui.row(AppPickerActivity.this);
                int p = Ui.dp(AppPickerActivity.this, 10);
                row.setPadding(p, p, p, p);
                ImageView iv = new ImageView(AppPickerActivity.this);
                row.addView(iv, new LinearLayout.LayoutParams(iconPx, iconPx));
                TextView t = Ui.title(AppPickerActivity.this, "");
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.leftMargin = Ui.dp(AppPickerActivity.this, 14);
                row.addView(t, lp);
            }
            ((ImageView) row.getChildAt(0)).setImageBitmap(e.icon);
            ((TextView) row.getChildAt(1)).setText(e.label);
            return row;
        }
    }
}
