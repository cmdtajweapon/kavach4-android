package com.cmdtaj.kavach4assistant;

import android.app.Application;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

/**
 * Initializes the Google Mobile Ads SDK exactly once, at process start,
 * as recommended by AdMob's setup guide. If initialization fails (no
 * network, Play Services unavailable, etc.) the app simply proceeds
 * without ads — see MainActivity's ad-load failure handling.
 */
public class KavachApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
                // No-op: MainActivity loads the banner independently and
                // already handles the case where ads never become ready.
            }
        });
    }
}
