package uy.vuelos.uruguay;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    private boolean foreground = false;
    private boolean autoRefresh = true;
    private boolean errorActive = false;
    private int mode = 0;
    private long lastLoadedElapsed = 0L;
    private boolean viewingOtherDate = false;
    private boolean manualBrowsing = false;
    private long lastDriverInteraction = 0L;

    private WebView panel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = () -> {
        if (RefreshModePolicy.shouldReload(foreground,autoRefresh,manualBrowsing,viewingOtherDate) && panel != null) {
            panel.reload();
            scheduleAutoRefresh();
        }
    };
    private ProgressBar progress;
    private FrameLayout browserFrame;
    private LinearLayout errorLayer;
    private TextView heading, subheading, localTime, arrivalTab, departureTab, status, autoSwitch, lastUpdate;
    private TextView refreshButton;
    private TextView driverToggle, alertsToggle;
    private TextView liveModeTab, manualModeTab, sourceWarning;
    private DriverBoard driverBoard;
    private List<FlightData.Flight> flights = new ArrayList<>();
    private int readToken = 0;
    private boolean priorityVisible = false;
    private boolean boardHasResults = false;
    private long sourceTapAt = 0L;

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
        FlightAlertReceiver.createChannel(this);
        renderAlertsToggle();
    }

    private void createInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(BACKGROUND);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(15), dp(9), dp(15), dp(11));
        hero.setBackgroundColor(NAVY);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_brand_plane);
        icon.setBackground(rounded(DARK, 14, 0xFF25465E, 1));
        icon.setPadding(dp(6), dp(6), dp(6), dp(6));
        top.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(11), 0, 0, 0);
        TextView title = label("VUELOS", 19, WHITE, true);
        title.setLetterSpacing(0.06f);
        TextView line2 = label("U R U G U A Y", 8, MINT, true);
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
        ep.topMargin = dp(10);
        hero.addView(eyebrow, ep);

        heading = label("Llegadas a Carrasco", 22, WHITE, true);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(5);
        hero.addView(heading, hp);
        subheading = label("Horarios y estado de vuelos", 11, 0xFFB7CBDA, false);
        hero.addView(subheading);

        LinearLayout tabBox = new LinearLayout(this);
        tabBox.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabBox.setBackground(rounded(DARK, 16, 0xFF34536A, 1));
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        tabsParams.topMargin = dp(10);
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
        info.setPadding(dp(14), dp(6), dp(14), dp(6));
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
        root.addView(sep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)));

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
        caption.setPadding(dp(9), dp(7), dp(9), dp(5));
        LinearLayout modeBar = new LinearLayout(this);
        modeBar.setPadding(dp(8),dp(4),dp(8),dp(2));
        modeBar.setGravity(Gravity.CENTER_VERTICAL);
        liveModeTab = label("●  HOY · EN VIVO",12,TEXT,true);
        liveModeTab.setGravity(Gravity.CENTER);
        liveModeTab.setPadding(dp(5),dp(8),dp(5),dp(8));
        liveModeTab.setOnClickListener(v -> returnToLive());
        manualModeTab = label("◷  CONSULTAR FECHA",12,TEXT,true);
        manualModeTab.setGravity(Gravity.CENTER);
        manualModeTab.setPadding(dp(5),dp(8),dp(5),dp(8));
        manualModeTab.setOnClickListener(v -> enterManualBrowsing(true));
        LinearLayout.LayoutParams liveLp = new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        liveLp.rightMargin=dp(4);
        modeBar.addView(liveModeTab,liveLp);
        LinearLayout.LayoutParams manualLp = new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        manualLp.leftMargin=dp(4);
        modeBar.addView(manualModeTab,manualLp);
        content.addView(modeBar);
        renderModeTabs();

        LinearLayout captionLine = new LinearLayout(this);
        captionLine.setGravity(Gravity.CENTER_VERTICAL);
        captionLine.addView(caption,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        driverToggle = label("PANEL ↗",11,0xFF167866,true);
        driverToggle.setPadding(dp(10),dp(8),dp(10),dp(8));
        driverToggle.setOnClickListener(v -> {
            if (priorityVisible) enterManualBrowsing(true);
            else if (boardHasResults) displayPriority(true);
            else enterManualBrowsing(true);
        });
        captionLine.addView(driverToggle);
        content.addView(captionLine);
        sourceWarning=label("Fuente sin datos · Consultar Carrasco oficial ↗",11,0xFF805216,true);
        sourceWarning.setBackground(rounded(0xFFFFF1D8,10,0,0));
        sourceWarning.setPadding(dp(12),dp(10),dp(12),dp(10));
        sourceWarning.setVisibility(View.GONE);
        sourceWarning.setOnClickListener(v->{
            try{startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://aeropuertodecarrasco.com.uy/panel-de-vuelos/")));}
            catch(Exception ignored){}
        });
        LinearLayout.LayoutParams warningLp=new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        warningLp.setMargins(dp(11),dp(3),dp(11),dp(7));
        content.addView(sourceWarning,warningLp);

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
        panel.setOnTouchListener((view, event) -> {
            if (mode == 0 && !priorityVisible) {
                if(event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    // Any manual interaction with Avionio freezes its current filter before auto reload.
                    enterManualBrowsing(false);
                }
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    sourceTapAt = SystemClock.elapsedRealtime();
                    final int token = ++readToken;
                    handler.postDelayed(() -> extractFlights(token,0),1250L);
                    handler.postDelayed(() -> extractFlights(token,1),3200L);
                    handler.postDelayed(() -> extractFlights(token,2),5900L);
                }
            }
            return false;
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
                    status.setText("Consultando vuelos · horarios orientativos");
                    if(mode == 0) {
                        final int token=++readToken;
                        handler.postDelayed(() -> extractFlights(token,0),950);
                        handler.postDelayed(() -> extractFlights(token,1),2900);
                    }
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
        FrameLayout flightFrame = new FrameLayout(this);
        flightFrame.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        driverBoard = new DriverBoard(this);
        driverBoard.setOnTouchListener((v,event)->{
            if(event.getAction() == android.view.MotionEvent.ACTION_DOWN ||
                event.getAction() == android.view.MotionEvent.ACTION_MOVE){
                lastDriverInteraction=SystemClock.elapsedRealtime();
            }
            return false;
        });
        driverBoard.setVisibility(View.GONE);
        flightFrame.addView(driverBoard, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(flightFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
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
        footer.setPadding(dp(13), dp(3), dp(13), dp(7));

        LinearLayout firstFooter = new LinearLayout(this);
        firstFooter.setGravity(Gravity.CENTER_VERTICAL);
        lastUpdate = label("Esperando primera consulta", 12, TEXT, true);
        firstFooter.addView(lastUpdate, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        autoSwitch = label("", 11, 0xFF0D8468, true);
        autoSwitch.setGravity(Gravity.CENTER);
        autoSwitch.setPadding(dp(9), dp(5), dp(9), dp(5));
        autoSwitch.setOnClickListener(v -> {
            if(manualBrowsing)returnToLive();
            else toggleAuto();
        });
        firstFooter.addView(autoSwitch);
        footer.addView(firstFooter);
        LinearLayout noticeBar = new LinearLayout(this);
        noticeBar.setGravity(Gravity.CENTER_VERTICAL);
        noticeBar.setPadding(0,dp(3),0,dp(3));
        TextView note=label("Avisos según última consulta",10,SECONDARY,false);
        noticeBar.addView(note,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        alertsToggle=label("",11,TEXT,true);
        alertsToggle.setGravity(Gravity.CENTER);
        alertsToggle.setPadding(dp(9),dp(6),dp(9),dp(6));
        alertsToggle.setOnClickListener(v -> toggleFlightAlerts());
        noticeBar.addView(alertsToggle);
        footer.addView(noticeBar);

        LinearLayout secondFooter = new LinearLayout(this);
        secondFooter.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(3);
        status = label("Horarios orientativos · Fuente externa", 10, SECONDARY, false);
        secondFooter.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView copyright = label("FUENTE ↗", 10, 0xFF267FA1, true);
        copyright.setContentDescription("Abrir la fuente de datos del panel");
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
        readToken++;
        flights.clear();
        boardHasResults = false;
        viewingOtherDate = false;
        manualBrowsing = false;
        if(sourceWarning!=null)sourceWarning.setVisibility(View.GONE);
        renderModeTabs();
        renderAuto();
        displayPriority(false);
        panel.loadUrl(mode == 0 ? ARRIVALS : DEPARTURES);
        scheduleAutoRefresh();
    }

    private void refreshNow() {
        if(panel==null)return;
        updateClock();
        errorLayer.setVisibility(View.GONE);
        if (manualBrowsing){
            // Reload would discard the date/time selected inside the provider's WebView.
            final int token=++readToken;
            status.setText("Consulta fija · Para cambiar el resultado, usá la flecha azul del panel");
            handler.postDelayed(() -> extractFlights(token,0),350);
            return;
        }
        panel.reload();
        scheduleAutoRefresh();
    }

    private void renderModeTabs(){
        if(liveModeTab==null||manualModeTab==null)return;
        liveModeTab.setTextColor(manualBrowsing?SECONDARY:NAVY);
        manualModeTab.setTextColor(manualBrowsing?NAVY:SECONDARY);
        liveModeTab.setBackground(rounded(manualBrowsing?0xFFF0F3F5:0xFFB6F1DE,13,0,0));
        manualModeTab.setBackground(rounded(manualBrowsing?0xFFB6F1DE:0xFFF0F3F5,13,0,0));
        liveModeTab.setContentDescription("Volver al día de hoy y reanudar la actualización automática");
        manualModeTab.setContentDescription("Consulta manual: conservar fecha y posición al refrescar");
    }

    private void enterManualBrowsing(boolean showSource) {
        if(mode!=0)return;
        if(!manualBrowsing){
            // On switching dates, the previous day's list is not a valid preview.
            flights.clear();
            boardHasResults=false;
            if(sourceWarning!=null)sourceWarning.setVisibility(View.GONE);
        }
        manualBrowsing=true;
        handler.removeCallbacks(refreshTask);
        if(showSource)displayPriority(false);
        renderModeTabs();
        renderAuto();
        if(status != null)status.setText("CONSULTA MANUAL · filtro protegido · actualización pausada");
    }

    private void returnToLive(){
        if(mode!=0){selectMode(0);return;}
        if(!manualBrowsing){
            if(priorityVisible&&driverBoard!=null) driverBoard.smoothScrollTo(0,0);
            return;
        }
        manualBrowsing=false;
        viewingOtherDate=false;
        readToken++;
        flights.clear();
        boardHasResults=false;
        if(sourceWarning!=null)sourceWarning.setVisibility(View.GONE);
        displayPriority(false);
        renderModeTabs();
        renderAuto();
        subheading.setText("Horarios y estado de los vuelos");
        // Only an explicit 'Hoy' action may reset the provider's date selector.
        panel.loadUrl(ARRIVALS);
        scheduleAutoRefresh();
    }


    private void displayPriority(boolean wantPriority) {
        priorityVisible = wantPriority && mode==0 && boardHasResults;
        if (driverBoard != null) driverBoard.setVisibility(priorityVisible?View.VISIBLE:View.GONE);
        if (panel != null) panel.setVisibility(priorityVisible?View.INVISIBLE:View.VISIBLE);
        if (driverToggle != null) driverToggle.setText(priorityVisible?"VER FILTRO ↗":boardHasResults?"VER RESUMEN ↗":"PANEL ORIGINAL ↗");
    }

    private void extractFlights(int token, int attempt) {
        if (token != readToken || mode!=0 || panel==null || errorActive) return;
        panel.evaluateJavascript(FlightData.EXTRACT_JS, payload -> {
            if (token!=readToken || mode!=0 || errorActive) return;
            long now=System.currentTimeMillis();
            FlightData.BoardSnapshot board=FlightData.parseBoardJs(payload,now);
            LocalDate today=Instant.ofEpochMilli(now).atZone(FlightData.UY).toLocalDate();
            if(board.parsed) {
                if(sourceWarning!=null){
                    sourceWarning.setText("La fuente no ofrece registros para esta fecha. No confirma ausencia de arribos. Ver panel oficial ↗");
                    sourceWarning.setVisibility(board.sourceEmpty?View.VISIBLE:View.GONE);
                }
                flights=board.flights;
                boardHasResults=true;
                viewingOtherDate=!today.equals(board.selectedDate);
                driverBoard.render(flights,board.selectedDate,board.sourceEmpty);
                if(RefreshModePolicy.shouldAutoFocus(!manualBrowsing,priorityVisible,
                      SystemClock.elapsedRealtime(),lastDriverInteraction) && driverBoard!=null){
                    driverBoard.post(() -> { if(!manualBrowsing)driverBoard.smoothScrollTo(0,0); });
                }
                // The source's date may change when a user selects tomorrow. Keep it visible.
                if(viewingOtherDate){
                    subheading.setText("Consulta: "+DateTimeFormatter.ofPattern("d MMM yyyy",new Locale("es","UY")).format(board.selectedDate));
                }else subheading.setText("Horarios y estado de los vuelos");
                if(viewingOtherDate && !manualBrowsing){
                    // Source may open tomorrow by default. Preserve the just-read rows.
                    manualBrowsing=true;
                    handler.removeCallbacks(refreshTask);
                    renderModeTabs();
                }
                if(!manualBrowsing) displayPriority(true);
                else displayPriority(false);
                renderAuto();
                scheduleAutoRefresh();
                if(board.sourceEmpty){
                    status.setText("Sin registros en la fuente · no confirma ausencia de vuelos");
                    // Do not cancel earlier alerts when the user browses a future date.
                }else if(viewingOtherDate){
                    status.setText(flights.size()+" arribos de la fecha consultada · alertas solo para hoy");
                }else if(FlightAlerts.enabled(this)){
                    int scheduled=FlightAlerts.schedule(this,flights,now);
                    status.setText(flights.size()+" llegadas · "+scheduled+" avisos programados");
                }else status.setText(flights.size()+" llegadas identificadas · datos externos");
            } else if(attempt<2){
                handler.postDelayed(() -> extractFlights(token,attempt+1),1600);
            } else {
                if(sourceWarning!=null){
                    sourceWarning.setText("No se pudieron verificar vuelos con este filtro. Comprobar Carrasco oficial ↗");
                    sourceWarning.setVisibility(View.VISIBLE);
                }
                flights.clear();
                boardHasResults=false;
                viewingOtherDate=false;
                displayPriority(false);
                status.setText("No se pudieron recuperar vuelos · el panel no confirma ausencia de arribos");
                // Preserve already scheduled alerts when browsing an unavailable future date.
            }
        });
    }

    private void renderAlertsToggle(){
        if(alertsToggle==null) return;
        boolean active=FlightAlerts.enabled(this);
        alertsToggle.setText(active?"● ALERTAS ON":"○ ACTIVAR ALERTAS");
        alertsToggle.setTextColor(active?0xFF0B785D:TEXT);
        alertsToggle.setBackground(rounded(active?0xFFE1F8EA:0xFFE8EDF2,14,0,0));
    }

    private void toggleFlightAlerts() {
        if(FlightAlerts.enabled(this)){
            FlightAlerts.setEnabled(this,false);
            renderAlertsToggle();
            status.setText("Alertas desactivadas");
            return;
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},303);
            return;
        }
        enableFlightAlerts();
    }

    private void enableFlightAlerts(){
        FlightAlerts.setEnabled(this,true);
        renderAlertsToggle();
        if(flights.isEmpty())status.setText("Alertas activadas · sin arribos identificados todavía");
        else {
            int scheduled=FlightAlerts.schedule(this,flights,System.currentTimeMillis());
            status.setText("Alertas activadas · "+scheduled+" avisos programados");
        }
    }

    @Override public void onRequestPermissionsResult(int code,String[] perms,int[] result){
        super.onRequestPermissionsResult(code,perms,result);
        if(code==303){
            if(result.length>0 && result[0]==PackageManager.PERMISSION_GRANTED)enableFlightAlerts();
            else status.setText("Sin permisos de notificación: alertas desactivadas");
        }
    }

    private void renderAuto() {
        if (autoSwitch == null) return;
        autoSwitch.setText(manualBrowsing?"◷ MANUAL FIJO":viewingOtherDate&&autoRefresh?"● AUTO PAUSA":autoRefresh?"● AUTO 3 MIN":"○ AUTO OFF");
        autoSwitch.setTextColor(autoRefresh ? 0xFF137961 : SECONDARY);
        autoSwitch.setBackground(rounded(autoRefresh ? 0xFFE0F9EF : 0xFFE8EDF1, 16, 0, 0));
        autoSwitch.setContentDescription(manualBrowsing?"Consulta manual fija; volver a Hoy para actualizar automáticamente":autoRefresh?"Desactivar actualización cada 3 minutos":"Activar actualización cada 3 minutos");
    }

    private void toggleAuto() {
        autoRefresh = !autoRefresh;
        getPreferences(MODE_PRIVATE).edit().putBoolean("autoRefresh", autoRefresh).apply();
        renderAuto();
        scheduleAutoRefresh();
    }

    private void scheduleAutoRefresh() {
        handler.removeCallbacks(refreshTask);
        if (RefreshModePolicy.shouldReload(foreground,autoRefresh,manualBrowsing,viewingOtherDate)) handler.postDelayed(refreshTask, REFRESH_MS);
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        updateClock();
        if (panel != null && autoRefresh && !manualBrowsing && !viewingOtherDate && lastLoadedElapsed > 0L
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
        out.putBoolean("manualBrowsing",manualBrowsing);
        out.putBoolean("viewingOtherDate",viewingOtherDate);
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
