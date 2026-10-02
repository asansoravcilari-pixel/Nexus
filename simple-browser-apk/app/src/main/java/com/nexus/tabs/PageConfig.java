package com.nexus.tabs;

import android.net.Uri;

public final class PageConfig {
    public final String id;
    public final String title;
    public final String url;

    public PageConfig(String id, String title, String url) {
        this.id = id;
        this.title = title;
        this.url = url;
    }

    public String host() {
        String host = Uri.parse(url).getHost();
        return host == null ? "" : host.toLowerCase();
    }
}
