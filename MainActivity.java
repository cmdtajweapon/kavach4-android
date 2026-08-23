package com.cmdtaj.kavach4assistant;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_RECORD_AUDIO = 1001;
    private static final String PREFS_NAME = "kavach_prefs";
    private static final String PREF_LANGUAGE = "language";

    private WebView webView;
    private FrameLayout adContainer;
    private AdView adView;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;
    private String pendingSpeakLang = "en";

    private long lastBackPressTime = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Kavach_Main);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        adContainer = findViewById(R.id.ad_container);
        adView = findViewById(R.id.ad_view);

        setupWebView();
        setupTextToSpeech();
        setupAdBanner();
        setupBackHandling();
    }

    // ------------------------------------------------------------------
    // WebView
    // ------------------------------------------------------------------

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true); // required for localStorage (language persistence)
        settings.setAllowFileAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.setWebViewClient(new WebViewClient() {
            // Keep all navigation to bundled assets inside the WebView
            // (offline knowledge base); anything else is not expected
            // since the app has no external links inside the WebView.
        });

        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");
        webView.loadUrl("file:///android_asset/www/index.html");
    }

    // ------------------------------------------------------------------
    // Speech recognition (native SpeechRecognizer, bridged to JS)
    // ------------------------------------------------------------------

    void startNativeListening(String lang) {
        if (!hasRecordAudioPermission()) {
            requestRecordAudioPermission();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            notifyJsSpeechError("unavailable");
            return;
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }

            @Override
            public void onError(int error) {
                String code;
                switch (error) {
                    case SpeechRecognizer.ERROR_NO_MATCH: code = "no-speech"; break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: code = "no-speech"; break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: code = "not-allowed"; break;
                    case SpeechRecognizer.ERROR_NETWORK:
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: code = "network"; break;
                    case SpeechRecognizer.ERROR_AUDIO: code = "audio-capture"; break;
                    default: code = "unknown"; break;
                }
                notifyJsSpeechError(code);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    notifyJsSpeechResult(matches.get(0));
                } else {
                    notifyJsSpeechError("no-speech");
                }
            }

            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });

        String localeTag = "hi".equals(lang) ? "hi-IN" : "en-IN";
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);

        try {
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            notifyJsSpeechError("unknown");
        }
    }

    void stopNativeListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    private void notifyJsSpeechResult(String text) {
        String safe = jsonQuote(text);
        webView.evaluateJavascript("window.onAndroidSpeechResult && window.onAndroidSpeechResult(" + safe + ");", null);
    }

    private void notifyJsSpeechError(String code) {
        String safe = jsonQuote(code);
        webView.evaluateJavascript("window.onAndroidSpeechError && window.onAndroidSpeechError(" + safe + ");", null);
    }

    // ------------------------------------------------------------------
    // Text-to-speech (native TextToSpeech, bridged to JS)
    // ------------------------------------------------------------------

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            ttsReady = (status == TextToSpeech.SUCCESS);
            if (ttsReady) {
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {
                        webView.post(() -> webView.evaluateJavascript(
                                "window.onAndroidTtsStart && window.onAndroidTtsStart();", null));
                    }
                    @Override public void onDone(String utteranceId) {
                        webView.post(() -> webView.evaluateJavascript(
                                "window.onAndroidTtsEnd && window.onAndroidTtsEnd();", null));
                    }
                    @Override public void onError(String utteranceId) {
                        webView.post(() -> webView.evaluateJavascript(
                                "window.onAndroidTtsEnd && window.onAndroidTtsEnd();", null));
                    }
                });
            }
        });
    }

    void speakNative(String text, String lang) {
        pendingSpeakLang = lang;
        if (!ttsReady || textToSpeech == null) {
            notifyJsTtsUnavailable();
            return;
        }
        Locale locale = "hi".equals(lang) ? new Locale("hi", "IN") : new Locale("en", "IN");
        int result = textToSpeech.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fall back to a generic locale of the same language, then
            // finally to device default, rather than failing outright.
            Locale fallback = "hi".equals(lang) ? new Locale("hi") : Locale.US;
            int fallbackResult = textToSpeech.setLanguage(fallback);
            if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                notifyJsTtsUnavailable();
                return;
            }
        }
        textToSpeech.setPitch(0.85f);
        textToSpeech.setSpeechRate(0.92f);
        Bundle params = new Bundle();
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, "kavach_utt_" + System.currentTimeMillis());
    }

    void stopSpeakingNative() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    private void notifyJsTtsUnavailable() {
        webView.evaluateJavascript(
                "window.onAndroidTtsError && window.onAndroidTtsError(" + jsonQuote("unavailable") + ");", null);
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    boolean hasRecordAudioPermission() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    void requestRecordAudioPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            webView.evaluateJavascript(
                    "window.onAndroidPermissionResult && window.onAndroidPermissionResult(" + granted + ");", null);
            if (!granted) {
                Toast.makeText(this, R.string.mic_permission_denied, Toast.LENGTH_LONG).show();
            }
        }
    }

    // ------------------------------------------------------------------
    // AdMob banner
    // ------------------------------------------------------------------

    private void setupAdBanner() {
        // REQUIRED: AdView throws IllegalStateException at loadAd() time
        // if no ad size has been set. A fixed standard banner (320x50dp)
        // matches the spec's "one banner ad, bottom of screen" requirement.
        adView.setAdSize(AdSize.BANNER);
        adView.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
        adView.setAdListener(new AdListener() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                // Ad failed to load for any reason (no fill, no network,
                // misconfiguration, etc). Hide the whole container so the
                // WebView reclaims that space and the app keeps working
                // exactly as if ads were never present.
                adContainer.setVisibility(View.GONE);
            }

            @Override
            public void onAdLoaded() {
                adContainer.setVisibility(View.VISIBLE);
            }
        });
        try {
            adView.loadAd(new AdRequest.Builder().build());
        } catch (Exception e) {
            adContainer.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------------
    // Back button handling — WebView history first, then double-press
    // to exit, so the app can never be closed by a single accidental tap.
    // ------------------------------------------------------------------

    private void setupBackHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                    return;
                }
                long now = System.currentTimeMillis();
                if (now - lastBackPressTime < 2000) {
                    finish();
                } else {
                    lastBackPressTime = now;
                    Toast.makeText(MainActivity.this, R.string.exit_confirm_toast, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Language preference (native mirror of the WebView's localStorage,
    // so it also survives a WebView data clear).
    // ------------------------------------------------------------------

    void saveLanguagePreference(String lang) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(PREF_LANGUAGE, lang).apply();
    }

    String getSavedLanguagePreference() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(PREF_LANGUAGE, "en");
    }

    // ------------------------------------------------------------------
    // Share / Rate / About / Privacy
    // ------------------------------------------------------------------

    void shareAppNative() {
        String url = getString(R.string.play_store_url);
        String text = getString(R.string.share_app_text, url);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app_chooser_title)));
    }

    void rateAppNative() {
        String pkg = getPackageName();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
        } catch (android.content.ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.play_store_url))));
        }
    }

    void openPrivacyPolicyNative() {
        // Loaded inside the same WebView (fully offline). The system
        // back button / WebView history handling above brings the user
        // straight back to the app.
        webView.loadUrl("file:///android_asset/www/privacy_policy.html");
    }

    void showAboutNative() {
        String body = getString(R.string.about_body, getAppVersionName());
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(body)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    String getAppVersionName() {
        return BuildConfig.VERSION_NAME;
    }

    // ------------------------------------------------------------------
    // Lifecycle cleanup
    // ------------------------------------------------------------------

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    private static String jsonQuote(String s) {
        try {
            return JSONObject.quote(s);
        } catch (Exception e) {
            return "\"\"";
        }
    }
}
