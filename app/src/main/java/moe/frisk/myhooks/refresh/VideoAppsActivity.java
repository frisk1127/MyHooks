package moe.frisk.myhooks.refresh;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import moe.frisk.myhooks.R;
import moe.frisk.myhooks.ui.ModuleActivity;

public final class VideoAppsActivity extends ModuleActivity {
    private final List<AppRows.Entry> apps = new ArrayList<>();
    private final List<AppRows.Entry> shown = new ArrayList<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private Set<String> selected;
    private AppRows adapter;
    private ListView list;
    private EditText search;
    private ImageButton clear;
    private TextView empty;
    private TabLayout.Tab selectedTab;
    private boolean selectedOnly;
    private boolean loading = true;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        selected = VideoRefreshConfig.packages(this);
        selectedOnly = state != null && state.getBoolean("selectedOnly");
        View page = getLayoutInflater().inflate(R.layout.activity_video_apps, null);
        showPage(page, true);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        findViewById(R.id.help).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.refresh_help).setMessage(R.string.refresh_help_body)
            .setPositiveButton(android.R.string.ok, null).show());
        findViewById(R.id.app_panel).setBackground(surface(com.google.android.material.R.attr.colorSurfaceContainer, 20));
        findViewById(R.id.search_region).setBackground(surface(com.google.android.material.R.attr.colorSurfaceContainerHigh, 26));

        list = findViewById(R.id.app_list);
        empty = findViewById(R.id.empty_list);
        search = findViewById(R.id.app_search);
        clear = findViewById(R.id.clear_search);
        adapter = new AppRows(this, shown, selected, workers);
        list.setAdapter(adapter);
        list.setEmptyView(empty);
        list.setOnItemClickListener((parent, view, position, id) -> toggle(shown.get(position).pkg));

        TabLayout tabs = findViewById(R.id.app_tabs);
        tabs.addTab(tabs.newTab().setId(R.id.all_apps_tab).setText(R.string.refresh_all_apps), !selectedOnly);
        selectedTab = tabs.newTab().setId(R.id.selected_apps_tab);
        updateCount();
        tabs.addTab(selectedTab, selectedOnly);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                selectedOnly = tab == selectedTab;
                filter(true);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        clear.setOnClickListener(v -> search.setText(""));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clear.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                filter(true);
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        search.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(v.getWindowToken(), 0);
            return true;
        });
        if (state != null) search.setText(state.getString("query", ""));
        Set<String> initial = new HashSet<>(selected);
        workers.execute(() -> loadApps(initial, state));
    }

    private void toggle(String pkg) {
        Set<String> next = new HashSet<>(selected);
        if (!next.remove(pkg)) next.add(pkg);
        if (!VideoRefreshConfig.save(this, next)) {
            Toast.makeText(this, R.string.refresh_save_error, Toast.LENGTH_SHORT).show();
            return;
        }
        selected.clear();
        selected.addAll(next);
        updateCount();
        filter(false);
    }

    private void loadApps(Set<String> initial, Bundle state) {
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Set<String> launchable = new HashSet<>();
        for (ResolveInfo app : pm.queryIntentActivities(launcher, 0)) launchable.add(app.activityInfo.packageName);
        List<AppRows.Entry> loaded = new ArrayList<>();
        Set<String> missing = new HashSet<>(initial);
        for (ApplicationInfo info : pm.getInstalledApplications(0)) {
            if (Thread.currentThread().isInterrupted()) return;
            missing.remove(info.packageName);
            loaded.add(new AppRows.Entry(info.loadLabel(pm).toString(), info.packageName, info));
        }
        for (String pkg : missing) loaded.add(new AppRows.Entry(pkg, pkg, null));
        Collator collator = Collator.getInstance();
        loaded.sort((a, b) -> {
            int checked = Boolean.compare(initial.contains(b.pkg), initial.contains(a.pkg));
            if (checked != 0) return checked;
            int launches = Boolean.compare(launchable.contains(b.pkg), launchable.contains(a.pkg));
            if (launches != 0) return launches;
            int name = collator.compare(a.label, b.label);
            return name != 0 ? name : a.pkg.compareTo(b.pkg);
        });
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            loading = false;
            apps.addAll(loaded);
            filter(false);
            if (state != null) {
                String anchor = state.getString("anchor");
                for (int i = 0; anchor != null && i < shown.size(); i++) {
                    if (shown.get(i).pkg.equals(anchor)) {
                        list.setSelectionFromTop(i, state.getInt("offset"));
                        break;
                    }
                }
            }
        });
    }

    private void filter(boolean resetScroll) {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (AppRows.Entry entry : apps) {
            if ((!selectedOnly || selected.contains(entry.pkg)) && entry.searchable.contains(query)) shown.add(entry);
        }
        int message = selectedOnly && selected.isEmpty() ? R.string.refresh_none_selected : R.string.refresh_no_match;
        empty.setText(loading ? R.string.refresh_loading : message);
        adapter.notifyDataSetChanged();
        if (resetScroll) list.setSelection(0);
    }

    private void updateCount() {
        selectedTab.setText(getString(R.string.refresh_selected_count, selected.size()));
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("query", search.getText().toString());
        state.putBoolean("selectedOnly", selectedOnly);
        int first = list.getFirstVisiblePosition();
        if (list.getChildCount() > 0 && first >= 0 && first < shown.size()) {
            state.putString("anchor", shown.get(first).pkg);
            state.putInt("offset", list.getChildAt(0).getTop() - list.getListPaddingTop());
        }
    }

    @Override protected void onDestroy() {
        workers.shutdownNow();
        super.onDestroy();
    }
}
