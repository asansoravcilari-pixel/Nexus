package com.nexus.tabs;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class PageStore {
    private static final String PREFS = "nexus_tabs_pages";
    private static final String KEY = "pages";
    private final SharedPreferences prefs;

    public PageStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<PageConfig> load() {
        List<PageConfig> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new PageConfig(o.getString("id"), o.getString("title"), o.getString("url")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public void save(List<PageConfig> pages) {
        JSONArray arr = new JSONArray();
        for (PageConfig page : pages) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", page.id);
                o.put("title", page.title);
                o.put("url", page.url);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString(KEY, arr.toString()).apply();
    }
}
