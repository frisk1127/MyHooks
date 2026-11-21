package moe.frisk.myhooks.refresh;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class VideoAppsActivity extends Activity {
    private final List<Entry> apps = new ArrayList<>();
    private final List<Entry> shown = new ArrayList<>();
    private Set<String> selected;
    private AppsAdapter adapter;
    private EditText search;
    private TextView count;
    private TextView empty;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        selected = VideoRefreshConfig.packages(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(8), dp(16), 0);
        page.setFocusableInTouchMode(true);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button back = new Button(this, null, android.R.attr.borderlessButtonStyle);
        back.setText("返回");
        back.setOnClickListener(v -> finish());
        header.addView(back);
        TextView title = new TextView(this);
        title.setText("高刷应用");
        title.setTextSize(22);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        count = new TextView(this);
        header.addView(count);
        page.addView(header);
        TextView hint = new TextView(this);
        hint.setText("重新进入所选应用生效。双芯画质增强仅作用于系统已适配的播放页。");
        page.addView(hint);
        search = new EditText(this);
        search.setSingleLine();
        search.setHint("搜索应用名称或包名");
        page.addView(search);
        ListView list = new ListView(this);
        adapter = new AppsAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            String pkg = shown.get(position).pkg;
            Set<String> next = new HashSet<>(selected);
            if (!next.remove(pkg)) next.add(pkg);
            if (VideoRefreshConfig.save(this, next)) selected = next;
            else Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
            updateCount();
            adapter.notifyDataSetChanged();
        });
        empty = new TextView(this);
        empty.setGravity(android.view.Gravity.CENTER);
        empty.setText("正在读取应用…");
        page.addView(empty, -1, dp(56));
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setEmptyView(empty);
        setContentView(page);
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(16) + insets.getSystemWindowInsetLeft(), dp(8) + insets.getSystemWindowInsetTop(),
                dp(16) + insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        page.requestApplyInsets();
        updateCount();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(); }
            @Override public void afterTextChanged(Editable e) {}
        });
        if (state != null) search.setText(state.getString("query", ""));
        Set<String> initial = new HashSet<>(selected);
        new Thread(() -> loadApps(initial), "MyHooks-AppList").start();
    }

    private void loadApps(Set<String> initial) {
        PackageManager pm = getPackageManager();
        List<Entry> loaded = new ArrayList<>();
        Set<String> missing = new HashSet<>(initial);
        for (ApplicationInfo info : pm.getInstalledApplications(0)) {
            missing.remove(info.packageName);
            loaded.add(new Entry(info.loadLabel(pm).toString(), info.packageName));
        }
        for (String pkg : missing) loaded.add(new Entry(pkg, pkg));
        Collator collator = Collator.getInstance();
        loaded.sort((a, b) -> {
            int checked = Boolean.compare(initial.contains(b.pkg), initial.contains(a.pkg));
            if (checked != 0) return checked;
            int byName = collator.compare(a.label, b.label);
            return byName != 0 ? byName : a.pkg.compareTo(b.pkg);
        });
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            apps.addAll(loaded);
            empty.setText("没有匹配的应用");
            filter();
        });
    }

    private void filter() {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Entry entry : apps) {
            if (entry.searchable.contains(query)) shown.add(entry);
        }
        adapter.notifyDataSetChanged();
    }

    private void updateCount() { count.setText("已选 " + selected.size()); }
    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density * n); }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("query", search.getText().toString());
    }

    private static final class Entry {
        final String label, pkg, searchable;
        Entry(String label, String pkg) {
            this.label = label;
            this.pkg = pkg;
            searchable = (label + "\n" + pkg).toLowerCase(Locale.ROOT);
        }
    }

    private final class AppsAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Entry getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            CheckedTextView row = recycled == null ? (CheckedTextView) getLayoutInflater()
                .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false) : (CheckedTextView) recycled;
            Entry entry = getItem(position);
            row.setSingleLine(false);
            row.setMinHeight(dp(60));
            row.setTextSize(16);
            SpannableString text = new SpannableString(entry.label + "\n" + entry.pkg);
            text.setSpan(new RelativeSizeSpan(0.8f), entry.label.length() + 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            row.setText(text);
            row.setChecked(selected.contains(entry.pkg));
            return row;
        }
    }
}
