package com.skyrush.game;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.AdError;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

public class MainActivity extends Activity {
    // SKY RUSH production ad unit IDs created in the owner's AdMob account.
    private static final String BANNER_AD_UNIT_ID = "ca-app-pub-2369000388451137/8530127989";
    private static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-2369000388451137/7025474623";

    private WebView webView;
    private LinearLayout rootLayout;
    private AdView bannerAd;
    private TextView bannerStatus;
    private InterstitialAd interstitialAd;
    private ConsentInformation consentInformation;
    private boolean adsInitialized = false;
    private boolean bannerAdded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rootLayout.addView(webView);

        // Temporary on-screen diagnostics for the missing Banner report.
        bannerStatus = new TextView(this);
        bannerStatus.setText("حالة Banner: بانتظار تهيئة الإعلانات");
        bannerStatus.setTextSize(10);
        bannerStatus.setTextColor(0xFF9FC7E8);
        bannerStatus.setGravity(android.view.Gravity.CENTER);
        bannerStatus.setPadding(6, 4, 6, 4);
        bannerStatus.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        rootLayout.addView(bannerStatus);

        setContentView(rootLayout);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.addJavascriptInterface(new AdsBridge(), "AndroidAds");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme()) && uri.toString().startsWith("file:///android_asset/")) {
                    return false;
                }
                if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) { }
                    return true;
                }
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/index.html");
        } else {
            webView.restoreState(savedInstanceState);
        }

        requestConsentAndInitializeAds();
    }

    private void requestConsentAndInitializeAds() {
        consentInformation = UserMessagingPlatform.getConsentInformation(this);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();

        consentInformation.requestConsentInfoUpdate(
                this,
                params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                        this,
                        formError -> {
                            if (consentInformation.canRequestAds()) {
                                initializeAdsOnce();
                            } else {
                                runOnUiThread(() -> bannerStatus.setText(
                                        "حالة Banner: لم تسمح إعدادات الموافقة بطلب الإعلانات"));
                            }
                        }),
                requestError -> {
                    // UMP may still permit ads using a valid consent choice from an earlier session.
                    if (consentInformation.canRequestAds()) {
                        initializeAdsOnce();
                    } else {
                        runOnUiThread(() -> bannerStatus.setText(
                                "حالة Banner: تعذّر تحديث الموافقة ولم يُسمح بطلب الإعلانات"));
                    }
                });
    }

    private void initializeAdsOnce() {
        if (adsInitialized) return;
        adsInitialized = true;
        MobileAds.initialize(this, initializationStatus -> runOnUiThread(() -> {
            loadBanner();
            loadInterstitial();
        }));
    }

    private void loadBanner() {
        if (isFinishing() || bannerAdded) return;
        bannerAd = new AdView(this);
        bannerAd.setAdUnitId(BANNER_AD_UNIT_ID);
        bannerAd.setAdSize(AdSize.BANNER);
        bannerAd.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                runOnUiThread(() -> bannerStatus.setText(
                        "حالة Banner: تم التحميل بنجاح"));
            }

            @Override
            public void onAdFailedToLoad(LoadAdError error) {
                runOnUiThread(() -> bannerStatus.setText(
                        "فشل Banner — الرمز: " + error.getCode()
                                + " — " + error.getMessage()));
            }
        });
        bannerStatus.setText("حالة Banner: أُرسل طلب الإعلان، بانتظار النتيجة...");
        bannerAd.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        rootLayout.addView(bannerAd);
        bannerAdded = true;
        bannerAd.loadAd(new AdRequest.Builder().build());
    }

    private void loadInterstitial() {
        if (!adsInitialized || isFinishing()) return;
        InterstitialAd.load(
                this,
                INTERSTITIAL_AD_UNIT_ID,
                new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialAd = ad;
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        interstitialAd = null;
                    }
                });
    }

    private void showInterstitialBetweenRounds() {
        runOnUiThread(() -> {
            if (isFinishing()) return;
            if (interstitialAd == null) {
                loadInterstitial();
                return;
            }

            InterstitialAd adToShow = interstitialAd;
            interstitialAd = null;
            adToShow.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    loadInterstitial();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(AdError error) {
                    loadInterstitial();
                }
            });
            adToShow.show(this);
        });
    }

    private class AdsBridge {
        @JavascriptInterface
        public void onFifthRoundCompleted() {
            showInterstitialBetweenRounds();
        }

        @JavascriptInterface
        public void showPrivacyOptionsForm() {
            runOnUiThread(() -> UserMessagingPlatform.showPrivacyOptionsForm(
                    MainActivity.this,
                    formError -> { }));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        if (bannerAd != null) bannerAd.pause();
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bannerAd != null) bannerAd.resume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (bannerAd != null) {
            bannerAd.destroy();
            bannerAd = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidAds");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
