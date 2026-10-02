package com.nexus.tabs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 7001;

    private final List<PageConfig> pages = new ArrayList<>();
    private final Map<String, WebView> webViews = new LinkedHashMap<>();
    private final Map<String, TextView> tabViews = new LinkedHashMap<>();

    private LinearLayout tabs;
    private FrameLayout content;
    private PageStore pageStore;
    private SecureStore secureStore;
    private String activePageId;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pageStore = new PageStore(this);
        secureStore = new SecureStore(this);
        pages.addAll(pageStore.load());
        buildUi();
        renderTabs();

        if (!pages.isEmpty()) {
            activate(pages.get(0).id);
        } else {
            showEmptyState();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(4), dp(3), dp(4), dp(3));
        top.setBackgroundColor(Color.rgb(17, 17, 17));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(tabs, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        top.addView(scroll, new LinearLayout.LayoutParams(0, dp(42), 1f));

        TextView add = new TextView(this);
        add.setText("+");
        add.setTextColor(Color.WHITE);
        add.setTextSize(25);
        add.setGravity(Gravity.CENTER);
        add.setContentDescription("Sayfa ekle");
        add.setOnClickListener(v -> showAddDialog());
        top.addView(add, new LinearLayout.LayoutParams(dp(46), dp(42)));

        content = new FrameLayout(this);
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void renderTabs() {
        tabs.removeAllViews();
        tabViews.clear();
        for (PageConfig page : pages) {
            TextView tab = new TextView(this);
            tab.setText(page.title);
            tab.setTextSize(14);
            tab.setGravity(Gravity.CENTER);
            tab.setSingleLine(true);
            tab.setPadding(dp(15), 0, dp(15), 0);
            tab.setOnClickListener(v -> activate(page.id));
            tab.setOnLongClickListener(v -> {
                confirmDelete(page);
                return true;
            });
            tabs.addView(tab, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
            tabViews.put(page.id, tab);
        }
        refreshTabStyle();
    }

    private void refreshTabStyle() {
        for (Map.Entry<String, TextView> entry : tabViews.entrySet()) {
            boolean active = entry.getKey().equals(activePageId);
            TextView tab = entry.getValue();
            tab.setTextColor(active ? Color.BLACK : Color.WHITE);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(12));
            bg.setColor(active ? Color.WHITE : Color.TRANSPARENT);
            tab.setBackground(bg);
        }
    }

    private void showEmptyState() {
        content.removeAllViews();
        TextView empty = new TextView(this);
        empty.setText("Henüz sayfa yok\n+ ile ekle");
        empty.setTextSize(18);
        empty.setTextColor(Color.DKGRAY);
        empty.setGravity(Gravity.CENTER);
        content.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void activate(String pageId) {
        PageConfig page = findPage(pageId);
        if (page == null) return;
        activePageId = pageId;
        WebView webView = webViews.get(pageId);
        if (webView == null) {
            webView = createWebView(page);
            webViews.put(pageId, webView);
        }
        content.removeAllViews();
        if (webView.getParent() != null) {
            ((ViewGroup) webView.getParent()).removeView(webView);
        }
        content.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (webView.getUrl() == null) {
            webView.loadUrl(page.url);
        }
        refreshTabStyle();
    }

    private WebView createWebView(PageConfig page) {
        WebView webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();

                if (("http".equals(scheme) || "https".equals(scheme)) && host.equals(page.host())) {
                    return false;
                }
                openExternal(uri);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                autofillIfSafe(view, page, url);
            }

            @Override
            public void onReceivedHttpAuthRequest(
                    WebView view, HttpAuthHandler handler, String host, String realm) {
                if (host != null && host.equalsIgnoreCase(page.host())) {
                    SecureStore.Credentials c = secureStore.get(page.id);
                    if (!c.isEmpty()) {
                        handler.proceed(c.username, c.password);
                        return;
                    }
                }
                handler.cancel();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Dosya seçici açılamadı", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });
        return webView;
    }

    private void autofillIfSafe(WebView webView, PageConfig page, String url) {
        Uri current = Uri.parse(url);
        String host = current.getHost() == null ? "" : current.getHost().toLowerCase();
        if (!host.equals(page.host())) return;

        SecureStore.Credentials c = secureStore.get(page.id);
        if (c.isEmpty()) return;

        String user = JSONObject.quote(c.username);
        String pass = JSONObject.quote(c.password);
        String js = "(function(){"
                + "const p=document.querySelector('input[type=password]');if(!p)return;"
                + "const f=p.form||document;"
                + "let u=f.querySelector('input[autocomplete=username],input[type=email],input[name*=user i],input[name*=email i],input[type=text]');"
                + "const set=(el,v)=>{if(!el)return;const d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d.set.call(el,v);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));};"
                + "set(u," + user + ");set(p," + pass + ");"
                + "})();";
        webView.evaluateJavascript(js, null);
    }

    private void showAddDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        box.setPadding(pad, dp(8), pad, 0);

        EditText title = field("Sayfa adı");
        EditText url = field("https://site.com");
        EditText username = field("Kullanıcı adı (isteğe bağlı)");
        EditText password = field("Şifre (isteğe bağlı)");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        box.addView(title);
        box.addView(url);
        box.addView(username);
        box.addView(password);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Sayfa ekle")
                .setView(box)
                .setNegativeButton("İptal", null)
                .setPositiveButton("Ekle", null)
                .create();

        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String t = title.getText().toString().trim();
            String raw = url.getText().toString().trim();
            String user = username.getText().toString();
            String pass = password.getText().toString();

            if (t.isEmpty()) {
                title.setError("Sayfa adı gerekli");
                return;
            }
            Uri uri = normalizeUrl(raw);
            if (uri == null || uri.getHost() == null) {
                url.setError("Geçerli bir http/https adresi gir");
                return;
            }
            if ("http".equalsIgnoreCase(uri.getScheme()) && (!user.isEmpty() || !pass.isEmpty())) {
                url.setError("Şifre kaydı için HTTPS kullan");
                return;
            }

            PageConfig page = new PageConfig(UUID.randomUUID().toString(), t, uri.toString());
            pages.add(page);
            pageStore.save(pages);
            secureStore.put(page.id, user, pass);
            renderTabs();
            activate(page.id);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setPadding(dp(4), dp(8), dp(4), dp(8));
        return e;
    }

    private Uri normalizeUrl(String raw) {
        try {
            if (raw.isEmpty()) return null;
            String value = raw.contains("://") ? raw : "https://" + raw;
            Uri uri = Uri.parse(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
            return uri;
        } catch (Exception e) {
            return null;
        }
    }

    private void confirmDelete(PageConfig page) {
        new AlertDialog.Builder(this)
                .setTitle("Sayfayı sil")
                .setMessage(page.title + " silinsin mi?")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Sil", (d, w) -> deletePage(page))
                .show();
    }

    private void deletePage(PageConfig page) {
        WebView old = webViews.remove(page.id);
        if (old != null) {
            old.stopLoading();
            old.destroy();
        }
        secureStore.remove(page.id);
        pages.remove(page);
        pageStore.save(pages);
        if (page.id.equals(activePageId)) activePageId = null;
        renderTabs();

        if (pages.isEmpty()) {
            showEmptyState();
        } else {
            activate(pages.get(0).id);
        }
    }

    private PageConfig findPage(String id) {
        for (PageConfig page : pages) {
            if (page.id.equals(id)) return page;
        }
        return null;
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(this, "Bağlantı açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        WebView current = activePageId == null ? null : webViews.get(activePageId);
        if (current != null && current.canGoBack()) {
            current.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && fileCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        for (WebView webView : webViews.values()) {
            webView.destroy();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
