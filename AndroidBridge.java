package com.cmdtaj.kavach4assistant;

import android.webkit.JavascriptInterface;

/**
 * Thin JS-facing wrapper around MainActivity's speech methods.
 *
 * The WebView calls these as window.AndroidBridge.xxx(...) from
 * www/index.html. Every method just forwards to MainActivity on the
 * main thread, since MainActivity owns the SpeechRecognizer and
 * TextToSpeech instances (both are Android framework objects that must
 * be created/used from the UI thread).
 *
 * IMPORTANT: kept as a standalone, non-obfuscated class — see
 * proguard-rules.pro, which explicitly preserves this class and its
 * method names for the release build, otherwise R8 renaming would
 * break the JS -> Java call bridge silently.
 */
public class AndroidBridge {

    private final MainActivity activity;

    AndroidBridge(MainActivity activity) {
        this.activity = activity;
    }

    /** Starts native speech recognition. lang is "en" or "hi". */
    @JavascriptInterface
    public void startListening(final String lang) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.startNativeListening(lang);
            }
        });
    }

    @JavascriptInterface
    public void stopListening() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.stopNativeListening();
            }
        });
    }

    /** Speaks text aloud using the native TextToSpeech engine. lang is "en" or "hi". */
    @JavascriptInterface
    public void speak(final String text, final String lang) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.speakNative(text, lang);
            }
        });
    }

    @JavascriptInterface
    public void stopSpeaking() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.stopSpeakingNative();
            }
        });
    }

    @JavascriptInterface
    public boolean hasRecordAudioPermission() {
        return activity.hasRecordAudioPermission();
    }

    @JavascriptInterface
    public void requestRecordAudioPermission() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.requestRecordAudioPermission();
            }
        });
    }

    /** Lets the web layer persist/restore the chosen language natively too (optional, mirrors localStorage). */
    @JavascriptInterface
    public void saveLanguagePreference(final String lang) {
        activity.saveLanguagePreference(lang);
    }

    @JavascriptInterface
    public String getSavedLanguagePreference() {
        return activity.getSavedLanguagePreference();
    }

    @JavascriptInterface
    public void shareApp() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.shareAppNative();
            }
        });
    }

    @JavascriptInterface
    public void rateApp() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.rateAppNative();
            }
        });
    }

    @JavascriptInterface
    public void openPrivacyPolicy() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.openPrivacyPolicyNative();
            }
        });
    }

    @JavascriptInterface
    public void showAbout() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.showAboutNative();
            }
        });
    }

    @JavascriptInterface
    public String getAppVersion() {
        return activity.getAppVersionName();
    }
}
