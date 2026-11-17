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

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
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
    // Google-provided test IDs. Replace with SKY RUSH's own IDs before public release.
    private static final String TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111";
    private static final String TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712";

    private WebView webView;
    private LinearLayout rootLayout;
    private AdView bannerAd;
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
                            }
                        }),
                requestError -> {
                    // UMP may still permit ads using a valid consent choice from an earlier session.
                    if (consentInformation.canRequestAds()) {
                        initializeAdsOnce();
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
        bannerAd.setAdUnitId(TEST_BANNER_ID);
        bannerAd.setAdSize(AdSize.BANNER);
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
                TEST_INTERSTITIAL_ID,
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
