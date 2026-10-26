package com.personal.jamestv9;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private static final String HOME = "https://james.david-lean.net/";
    private FrameLayout root;
    private WebView browser;
    private View videoFullScreen;
    private WebChromeClient.CustomViewCallback videoCallback;
    private boolean navReady;
    private boolean playerMode;
    private String navSource;
    private long previousKeyTime;

    private void fullscreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        fullscreen();
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(13,24,39));
        setContentView(root);
        try {
            navSource = getAsset("tv_navigation.js");
            browser = new WebView(this);
            browser.setBackgroundColor(Color.BLACK);
            browser.setFocusable(true);
            browser.setFocusableInTouchMode(true);
            WebSettings s = browser.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setSupportMultipleWindows(false);
            s.setJavaScriptCanOpenWindowsAutomatically(false);
            s.setBuiltInZoomControls(false);
            s.setDisplayZoomControls(false);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);
            browser.setWebViewClient(new WebViewClient() {
                @Override public void onPageStarted(WebView v, String url, Bitmap icon) { navReady = false; }
                @Override public void onPageFinished(WebView v, String url) {
                    CookieManager.getInstance().flush();
                    browser.evaluateJavascript(navSource + "\n;!!window.__jamesTvNav", value -> navReady = "true".equals(value));
                }
                @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                    Uri uri = req.getUrl();
                    String scheme = uri.getScheme();
                    if (scheme == null || scheme.equals("http") || scheme.equals("https")) return false;
                    if (req.isForMainFrame()) {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                        catch (Exception ignored) { Toast.makeText(MainActivity.this, "برنامه مناسب این لینک نصب نیست", Toast.LENGTH_SHORT).show(); }
                    }
                    return true;
                }
                @Override public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError error) {
                    if (req.isForMainFrame()) Toast.makeText(MainActivity.this, "سایت بارگذاری نشد؛ اینترنت و WebView را بررسی کنید", Toast.LENGTH_LONG).show();
                }
            });
            browser.setWebChromeClient(new WebChromeClient() {
                @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                    if (videoFullScreen != null) { callback.onCustomViewHidden(); return; }
                    videoFullScreen = view;
                    videoCallback = callback;
                    browser.setVisibility(View.GONE);
                    root.addView(view, new FrameLayout.LayoutParams(-1,-1));
                    view.requestFocus();
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
                @Override public void onHideCustomView() { closeVideo(); }
            });
            root.addView(browser, new FrameLayout.LayoutParams(-1,-1));
            browser.requestFocus();
            browser.loadUrl(HOME);
        } catch (Throwable e) {
            TextView message = new TextView(this);
            message.setText("James TV: unable to start WebView. Enable or update Android System WebView.\n" + e.getClass().getSimpleName());
            message.setTextColor(Color.WHITE);
            message.setTextSize(20);
            message.setGravity(Gravity.CENTER);
            root.addView(message, new FrameLayout.LayoutParams(-1,-1));
        }
    }
    private String getAsset(String name) throws Exception {
        try (InputStream in = getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int count;
            while ((count = in.read(buf)) != -1) out.write(buf,0,count);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    private void closeVideo() {
        if (videoFullScreen == null) return;
        root.removeView(videoFullScreen);
        videoFullScreen = null;
        if (videoCallback != null) { videoCallback.onCustomViewHidden(); videoCallback = null; }
        if (browser != null) {
            browser.setVisibility(View.VISIBLE);
            browser.requestFocus();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fullscreen();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_MENU || key == KeyEvent.KEYCODE_INFO) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                playerMode = !playerMode;
                Toast.makeText(this, playerMode ? "حالت کنترل مستقیم پلیر" : "انتخاب آیتم‌ها با ریموت", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        if (browser == null || videoFullScreen != null || playerMode || !navReady) return super.dispatchKeyEvent(event);
        boolean ok = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER;
        boolean direction = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (ok) {
            if (event.getAction() == KeyEvent.ACTION_UP) tapSelected();
            return true;
        }
        if (direction) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                long now = SystemClock.uptimeMillis();
                if (event.getRepeatCount() <= 1 || now - previousKeyTime > 65) {
                    previousKeyTime = now;
                    String dir = key == KeyEvent.KEYCODE_DPAD_UP ? "up" :
                      key == KeyEvent.KEYCODE_DPAD_DOWN ? "down" :
                      key == KeyEvent.KEYCODE_DPAD_LEFT ? "left" : "right";
                    browser.evaluateJavascript("window.__jamesTvNav&&window.__jamesTvNav.move('" + dir + "')", null);
                }
            }
            return true;
        }
        if (key == KeyEvent.KEYCODE_CHANNEL_UP || key == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                browser.evaluateJavascript("window.scrollBy(0,innerHeight*" +
                    (key == KeyEvent.KEYCODE_CHANNEL_UP ? "-0.8" : "0.8") + ")",null);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    private void tapSelected() {
        if (browser == null) return;
        browser.evaluateJavascript("JSON.stringify(window.__jamesTvNav && window.__jamesTvNav.activate())", value -> {
            if (browser == null || value == null) return;
            try {
                String payload = new JSONArray("[" + value + "]").getString(0);
                JSONObject point = new JSONObject(payload);
                if (!point.optBoolean("ok")) return;
                float x = (float)point.getDouble("x") * browser.getWidth();
                float y = (float)point.getDouble("y") * browser.getHeight();
                long time = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x,y,0);
                MotionEvent up = MotionEvent.obtain(time, time+65, MotionEvent.ACTION_UP, x,y,0);
                try { browser.dispatchTouchEvent(down); browser.dispatchTouchEvent(up); }
                finally { down.recycle(); up.recycle(); }
            } catch (Exception ignored) {}
        });
    }
    @Override public void onBackPressed() {
        if (videoFullScreen != null) { closeVideo(); return; }
        if (browser != null && browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }
    @Override protected void onPause() {
        if (browser != null) { browser.onPause(); CookieManager.getInstance().flush(); }
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (browser != null) browser.onResume();
        fullscreen();
    }
    @Override protected void onDestroy() {
        if (videoFullScreen != null) closeVideo();
        if (browser != null) {
            root.removeView(browser);
            browser.stopLoading();
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
