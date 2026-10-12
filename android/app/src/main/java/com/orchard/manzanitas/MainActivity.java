package com.orchard.manzanitas;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView game;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        game = new WebView(this);
        game.setBackgroundColor(0xFF071B17);
        game.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        game.getSettings().setJavaScriptEnabled(true);
        game.getSettings().setDomStorageEnabled(true);
        game.getSettings().setAllowFileAccess(true);
        game.getSettings().setMediaPlaybackRequiresUserGesture(true);
        game.setWebChromeClient(new WebChromeClient());
        game.setWebViewClient(new WebViewClient());
        setContentView(game);
        game.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onPause(){super.onPause();if(game!=null)game.onPause();}
    @Override protected void onResume(){super.onResume();if(game!=null)game.onResume();}
    @Override public void onBackPressed(){
        if(game!=null){game.evaluateJavascript("(function(){var overlay=document.getElementById('overlay');if(overlay && !overlay.hidden) return;})()",null);}
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){if(game!=null){game.destroy();game=null;}super.onDestroy();}
}
