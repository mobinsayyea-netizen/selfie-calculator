package com.mobeen.selfiecalculator;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows assets/index.html in a WebView. Also provides: camera/mic/location permission grants,
 * the system file picker for input type=file, and window.AndroidTTS.speak(text, rate) /
 * window.AndroidTTS.stop() because Android's WebView has no working speechSynthesis.
 */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

  private static final int REQ_PERMS = 100;
  private static final int REQ_FILE = 101;
  private static final String[] WANTED = new String[] {};

  private WebView webView;
  private TextToSpeech tts;
  private boolean ttsReady = false;
  private ValueCallback<Uri[]> filePathCallback;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    tts = new TextToSpeech(this, this);

    webView = new WebView(this);
    WebSettings s = webView.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    s.setGeolocationEnabled(true);
    webView.addJavascriptInterface(new TtsBridge(), "AndroidTTS");
    webView.setWebViewClient(new WebViewClient());
    webView.setWebChromeClient(
        new WebChromeClient() {
          @Override
          public void onPermissionRequest(final PermissionRequest request) {
            runOnUiThread(() -> request.grant(request.getResources()));
          }

          @Override
          public void onGeolocationPermissionsShowPrompt(
              String origin, GeolocationPermissions.Callback callback) {
            callback.invoke(origin, true, false);
          }

          @Override
          public boolean onShowFileChooser(
              WebView view,
              ValueCallback<Uri[]> callback,
              WebChromeClient.FileChooserParams params) {
            if (filePathCallback != null) filePathCallback.onReceiveValue(null);
            filePathCallback = callback;
            try {
              startActivityForResult(params.createIntent(), REQ_FILE);
            } catch (ActivityNotFoundException e) {
              filePathCallback = null;
              callback.onReceiveValue(null);
              return false;
            }
            return true;
          }
        });
    setContentView(webView);
    webView.loadUrl("file:///android_asset/index.html");
    askPermissions();
  }

  private void askPermissions() {
    List<String> need = new ArrayList<>();
    for (String p : WANTED) {
      if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
    }
    if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMS);
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
    super.onRequestPermissionsResult(code, perms, results);
    if (code == REQ_PERMS) webView.reload();
  }

  @Override
  protected void onActivityResult(int req, int res, Intent data) {
    if (req == REQ_FILE && filePathCallback != null) {
      filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
      filePathCallback = null;
      return;
    }
    super.onActivityResult(req, res, data);
  }

  @Override
  public void onBackPressed() {
    if (webView.canGoBack()) webView.goBack();
    else super.onBackPressed();
  }

  @Override
  public void onInit(int status) {
    if (status == TextToSpeech.SUCCESS) {
      ttsReady = true;
      tts.setLanguage(new Locale("hi", "IN"));
    }
  }

  @Override
  protected void onDestroy() {
    if (tts != null) {
      tts.stop();
      tts.shutdown();
    }
    super.onDestroy();
  }

  private class TtsBridge {
    @JavascriptInterface
    public void speak(String text, float rate) {
      if (!ttsReady || text == null || text.trim().isEmpty()) return;
      runOnUiThread(
          () -> {
            boolean hasDevanagari = text.chars().anyMatch(c -> c >= 0x0900 && c <= 0x097F);
            tts.setLanguage(hasDevanagari ? new Locale("hi", "IN") : new Locale("en", "IN"));
            tts.setSpeechRate(rate > 0 ? rate : 1f);
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, "app");
          });
    }

    @JavascriptInterface
    public void stop() {
      runOnUiThread(
          () -> {
            if (ttsReady) tts.stop();
          });
    }
  }
}
