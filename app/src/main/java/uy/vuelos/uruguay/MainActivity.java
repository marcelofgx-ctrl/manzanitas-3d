package uy.vuelos.uruguay;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class MainActivity extends Activity {
    private static final int NAVY = 0xFF0A2036;
    private static final int DARK = 0xFF102A43;
    private static final int MINT = 0xFF5CE2B8;
    private static final int WHITE = Color.WHITE;
    private static final int TEXT = 0xFF12293E;
    private static final int SECONDARY = 0xFF728496;
    private static final int BACKGROUND = 0xFFF4F7FA;
    private static final long REFRESH_MS = 180000L;
    private static final String ARRIVALS = "https://www.avionio.com/widget/es/mvd/arrivals?style=3";
    private static final String DEPARTURES = "https://www.avionio.com/widget/es/mvd/departures?style=3";
    private static final TimeZone UY = TimeZone.getTimeZone("America/Montevideo");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = () -> {
        if (foreground && autoRefresh && panel != null) {
            panel.reload();
            scheduleAutoRefresh();
        }
    };
    private boolean foreground = false;
    private boolean autoRefresh = true;
    private boolean errorActive = false;
    private int mode = 0;
    private long lastLoadedElapsed = 0L;

    private WebView panel;
    private ProgressBar progress;
    private FrameLayout browserFrame;
    private LinearLayout errorLayer;
    private TextView heading, subheading, localTime, arrivalTab, departureTab, status, autoSwitch, lastUpdate;
    private TextView refreshButton;

    private int dp(float points) {
        return Math.round(points * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int fill, int radius, int borderColor, int borderWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (borderWidth > 0) d.setStroke(dp(borderWidth), borderColor);
        return d;
    }

    private TextView label(String caption, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(caption);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setFontFeatureSettings("kern");
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    private String localClock() {
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", new Locale("es", "UY"));
        fmt.setTimeZone(UY);
        return fmt.format(new Date());
    }

    private String localDate() {
        SimpleDateFormat fmt = new SimpleDateFormat("EEE d MMM", new Locale("es", "UY"));
        fmt.setTimeZone(UY);
        return fmt.format(new Date());
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        autoRefresh = getPreferences(MODE_PRIVATE).getBoolean("autoRefresh", true);
        createInterface();
        selectMode(savedInstanceState == null ? 0 : savedInstanceState.getInt("mode", 0));
        renderAuto();
    }

    private void createInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(BACKGROUND);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(17), dp(18), dp(17));
        hero.setBackgroundColor(NAVY);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_brand_plane);
        icon.setBackground(rounded(DARK, 14, 0xFF25465E, 1));
        icon.setPadding(dp(6), dp(6), dp(6), dp(6));
        top.addView(icon, new LinearLayout.LayoutParams(dp(47), dp(47)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(11), 0, 0, 0);
        TextView title = label("VUELOS", 21, WHITE, true);
        title.setLetterSpacing(0.06f);
        TextView line2 = label("U R U G U A Y", 9, MINT, true);
        line2.setLetterSpacing(0.13f);
        brand.addView(title);
        brand.addView(line2);
        top.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        refreshButton = label("↻", 27, WHITE, false);
        refreshButton.setGravity(Gravity.CENTER);
        refreshButton.setBackground(rounded(DARK, 13, 0xFF375269, 1));
        refreshButton.setContentDescription("Actualizar vuelos ahora");
        refreshButton.setOnClickListener(v -> {
            refreshButton.animate().rotationBy(360f).setDuration(440).start();
            refreshNow();
        });
        top.addView(refreshButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        hero.addView(top);

        TextView eyebrow = label("AEROPUERTO INTERNACIONAL · MVD", 10, MINT, true);
        eyebrow.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = dp(20);
        hero.addView(eyebrow, ep);

        heading = label("Llegadas a Carrasco", 26, WHITE, true);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(5);
        hero.addView(heading, hp);
        subheading = label("Horarios y estado de los vuelos", 13, 0xFFB7CBDA, false);
        hero.addView(subheading);

        LinearLayout tabBox = new LinearLayout(this);
        tabBox.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabBox.setBackground(rounded(DARK, 16, 0xFF34536A, 1));
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(51));
        tabsParams.topMargin = dp(18);
        arrivalTab = label("✈  Llegadas", 14, NAVY, true);
        arrivalTab.setGravity(Gravity.CENTER);
        arrivalTab.setContentDescription("Ver arribos al aeropuerto de Carrasco");
        departureTab = label("↗  Salidas", 14, WHITE, true);
        departureTab.setGravity(Gravity.CENTER);
        departureTab.setContentDescription("Ver partidas del aeropuerto de Carrasco");
        tabBox.addView(arrivalTab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        tabBox.addView(departureTab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        arrivalTab.setOnClickListener(v -> selectMode(0));
        departureTab.setOnClickListener(v -> selectMode(1));
        hero.addView(tabBox, tabsParams);
        root.addView(hero);

        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(17), dp(11), dp(17), dp(11));
        info.setBackgroundColor(WHITE);
        LinearLayout infoLeft = new LinearLayout(this);
        infoLeft.setOrientation(LinearLayout.VERTICAL);
        TextView i1 = label("HORARIO DE MONTEVIDEO", 10, SECONDARY, true);
        localTime = label("", 14, TEXT, true);
        infoLeft.addView(i1);
        infoLeft.addView(localTime);
        info.addView(infoLeft, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView pill = label("MVD  ·  UY", 11, 0xFF147962, true);
        pill.setBackground(rounded(0xFFDEF8EE, 16, 0, 0));
        pill.setPadding(dp(11), dp(7), dp(11), dp(7));
        info.addView(pill);
        root.addView(info);
        updateClock();

        LinearLayout sep = new LinearLayout(this);
        sep.setBackgroundColor(BACKGROUND);
        root.addView(sep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)));

        FrameLayout surface = new FrameLayout(this);
        surface.setBackground(rounded(WHITE, 16, 0xFFE0E8EF, 1));
        surface.setClipToOutline(true);
        surface.setElevation(dp(2));
        LinearLayout.LayoutParams surfaceParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        surfaceParams.leftMargin = dp(11);
        surfaceParams.rightMargin = dp(11);
        surfaceParams.bottomMargin = dp(10);
        browserFrame = surface;

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView caption = label("   MOVIMIENTOS DE VUELOS", 11, 0xFF5C788D, true);
        caption.setLetterSpacing(0.08f);
        caption.setPadding(dp(10), dp(12), dp(10), dp(9));
        content.addView(caption);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(MINT));
        content.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        panel = new WebView(this);
        panel.setBackgroundColor(WHITE);
        WebSettings w = panel.getSettings();
        w.setJavaScriptEnabled(true);
        w.setDomStorageEnabled(true);
        w.setUseWideViewPort(true);
        w.setLoadWithOverviewMode(true);
        w.setBuiltInZoomControls(true);
        w.setDisplayZoomControls(false);
        w.setAllowFileAccess(false);
        w.setAllowContentAccess(false);
        w.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        w.setJavaScriptCanOpenWindowsAutomatically(false);
        panel.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        panel.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri target = request.getUrl();
                String host = target.getHost(), path = target.getPath();
                if ("https".equalsIgnoreCase(target.getScheme())
                    && "www.avionio.com".equalsIgnoreCase(host)
                    && path != null && path.startsWith("/widget/es/mvd/")) {
                    return false;
                }
                if ("https".equalsIgnoreCase(target.getScheme())
                    || "http".equalsIgnoreCase(target.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, target)); } catch (Exception ignored) { }
                }
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                errorActive = false;
                errorLayer.setVisibility(View.GONE);
                status.setText("Consultando información…");
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!errorActive) {
                    lastLoadedElapsed = SystemClock.elapsedRealtime();
                    lastUpdate.setText("Consultado " + localClock());
                    status.setText("Fuente Avionio · Horarios sujetos a cambios");
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    errorActive = true;
                    errorLayer.setVisibility(View.VISIBLE);
                    status.setText("No se pudo consultar la fuente");
                }
            }
        });
        content.addView(panel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        surface.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        errorLayer = new LinearLayout(this);
        errorLayer.setBackgroundColor(WHITE);
        errorLayer.setGravity(Gravity.CENTER);
        errorLayer.setOrientation(LinearLayout.VERTICAL);
        errorLayer.setPadding(dp(25), dp(20), dp(25), dp(20));
        TextView e1 = label("Sin conexión con los vuelos", 18, TEXT, true);
        e1.setGravity(Gravity.CENTER);
        errorLayer.addView(e1);
        TextView e2 = label("Revisá Internet y volvé a consultar. No mostramos vuelos ficticios.", 13, SECONDARY, false);
        e2.setGravity(Gravity.CENTER);
        e2.setPadding(0, dp(10), 0, dp(12));
        errorLayer.addView(e2);
        TextView retry = label("REINTENTAR", 13, NAVY, true);
        retry.setGravity(Gravity.CENTER);
        retry.setPadding(dp(22), dp(12), dp(22), dp(12));
        retry.setBackground(rounded(MINT, 13, 0, 0));
        retry.setOnClickListener(v -> refreshNow());
        errorLayer.addView(retry);
        errorLayer.setVisibility(View.GONE);
        surface.addView(errorLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(surface, surfaceParams);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(16), dp(6), dp(16), dp(12));

        LinearLayout firstFooter = new LinearLayout(this);
        firstFooter.setGravity(Gravity.CENTER_VERTICAL);
        lastUpdate = label("Esperando primera consulta", 12, TEXT, true);
        firstFooter.addView(lastUpdate, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        autoSwitch = label("", 11, 0xFF0D8468, true);
        autoSwitch.setGravity(Gravity.CENTER);
        autoSwitch.setPadding(dp(10), dp(7), dp(10), dp(7));
        autoSwitch.setOnClickListener(v -> toggleAuto());
        firstFooter.addView(autoSwitch);
        footer.addView(firstFooter);

        LinearLayout secondFooter = new LinearLayout(this);
        secondFooter.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(6);
        status = label("Fuente Avionio · Horarios sujetos a cambios", 10, SECONDARY, false);
        secondFooter.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView copyright = label("AVIONIO", 10, 0xFF267FA1, true);
        copyright.setContentDescription("Abrir la fuente Avionio");
        copyright.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.avionio.com/es"))); }
            catch (Exception ignored) {}
        });
        secondFooter.addView(copyright);
        footer.addView(secondFooter, sp);
        root.addView(footer);
        setContentView(root);
    }

    private void updateClock() {
        if (localTime != null) localTime.setText(localDate() + "  ·  " + localClock() + " h");
    }

    private void selectMode(int chosen) {
        mode = chosen == 1 ? 1 : 0;
        heading.setText(mode == 0 ? "Llegadas a Carrasco" : "Salidas de Carrasco");
        arrivalTab.setTextColor(mode == 0 ? NAVY : WHITE);
        departureTab.setTextColor(mode == 1 ? NAVY : WHITE);
        arrivalTab.setBackground(rounded(mode == 0 ? MINT : DARK, 13, 0, 0));
        departureTab.setBackground(rounded(mode == 1 ? MINT : DARK, 13, 0, 0));
        errorLayer.setVisibility(View.GONE);
        updateClock();
        panel.loadUrl(mode == 0 ? ARRIVALS : DEPARTURES);
        scheduleAutoRefresh();
    }

    private void refreshNow() {
        if (panel == null) return;
        updateClock();
        errorLayer.setVisibility(View.GONE);
        panel.reload();
        scheduleAutoRefresh();
    }

    private void renderAuto() {
        if (autoSwitch == null) return;
        autoSwitch.setText(autoRefresh ? "●  AUTO 3 MIN" : "○  AUTO OFF");
        autoSwitch.setTextColor(autoRefresh ? 0xFF137961 : SECONDARY);
        autoSwitch.setBackground(rounded(autoRefresh ? 0xFFE0F9EF : 0xFFE8EDF1, 16, 0, 0));
        autoSwitch.setContentDescription(autoRefresh ? "Desactivar actualización cada 3 minutos" : "Activar actualización cada 3 minutos");
    }

    private void toggleAuto() {
        autoRefresh = !autoRefresh;
        getPreferences(MODE_PRIVATE).edit().putBoolean("autoRefresh", autoRefresh).apply();
        renderAuto();
        scheduleAutoRefresh();
    }

    private void scheduleAutoRefresh() {
        handler.removeCallbacks(refreshTask);
        if (foreground && autoRefresh) handler.postDelayed(refreshTask, REFRESH_MS);
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        updateClock();
        if (panel != null && autoRefresh && lastLoadedElapsed > 0L
            && SystemClock.elapsedRealtime() - lastLoadedElapsed >= REFRESH_MS) {
            panel.reload();
        }
        scheduleAutoRefresh();
    }

    @Override protected void onPause() {
        foreground = false;
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("mode", mode);
        super.onSaveInstanceState(out);
    }

    @Override public void onBackPressed() {
        if (mode == 1) selectMode(0);
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(refreshTask);
        if (panel != null) panel.destroy();
        super.onDestroy();
    }
}
