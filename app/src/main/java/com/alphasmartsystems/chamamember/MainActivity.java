package com.alphasmartsystems.chamamember;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URI;
import java.net.URISyntaxException;

public class MainActivity extends Activity {
    private static final String PREFS = "alpha_chama_member";
    private static final String KEY_SERVER = "server_url";
    private static final int FILE_CHOOSER_REQUEST = 5102;
    private WebView webView;
    private LinearLayout setupPanel, errorPanel;
    private EditText serverUrlInput;
    private TextView errorText;
    private SharedPreferences prefs;
    private ValueCallback<Uri[]> filePathCallback;
    private String currentServer;
    private boolean mainFrameFailed = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        webView = findViewById(R.id.webView);
        setupPanel = findViewById(R.id.setupPanel);
        errorPanel = findViewById(R.id.errorPanel);
        serverUrlInput = findViewById(R.id.serverUrl);
        errorText = findViewById(R.id.errorText);
        Button connectButton = findViewById(R.id.connectButton);
        Button retryButton = findViewById(R.id.retryButton);
        Button changeServerButton = findViewById(R.id.changeServerButton);
        configureWebView();
        connectButton.setOnClickListener(v -> saveAndConnect());
        retryButton.setOnClickListener(v -> connectToServer());
        changeServerButton.setOnClickListener(v -> showSetup(true));
        currentServer = normalizeUrl(prefs.getString(KEY_SERVER, ""));
        if (currentServer.isEmpty()) showSetup(false); else connectToServer();
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(true); settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true); settings.setSupportZoom(false);
        settings.setUserAgentString(settings.getUserAgentString() + " AlphaChamaMember/1.0");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) { mainFrameFailed=false; errorPanel.setVisibility(View.GONE); webView.setVisibility(View.VISIBLE); }
            @Override public void onPageFinished(WebView view, String url) { if (!mainFrameFailed) { errorPanel.setVisibility(View.GONE); webView.setVisibility(View.VISIBLE); } }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) { if (request.isForMainFrame()) { mainFrameFailed=true; showConnectionError("Unable to connect to the Chama server. Check that the server is running and that this phone can reach it."); } }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri=request.getUrl(); String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase();
                if (scheme.equals("http") || scheme.equals("https")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException ex) { Toast.makeText(MainActivity.this,"No app is available to open this link.",Toast.LENGTH_SHORT).show(); }
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null); filePathCallback=cb;
                try { startActivityForResult(p.createIntent(), FILE_CHOOSER_REQUEST); return true; }
                catch (ActivityNotFoundException e) { filePathCallback=null; Toast.makeText(MainActivity.this,"No file picker is available.",Toast.LENGTH_SHORT).show(); return false; }
            }
        });
        webView.setDownloadListener((url,userAgent,contentDisposition,mimeType,contentLength) -> { try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); } catch(Exception e) { Toast.makeText(this,"Unable to open the download.",Toast.LENGTH_SHORT).show(); } });
        webView.setOnLongClickListener(v -> { showAppMenu(); return true; });
    }

    private void saveAndConnect() { String entered=normalizeUrl(serverUrlInput.getText().toString().trim()); if(!isValidHttpUrl(entered)){serverUrlInput.setError("Enter a valid http:// or https:// server address");return;} currentServer=entered; prefs.edit().putString(KEY_SERVER,currentServer).apply(); connectToServer(); }
    private void connectToServer() { currentServer=normalizeUrl(prefs.getString(KEY_SERVER,currentServer==null?"":currentServer)); if(currentServer.isEmpty()){showSetup(false);return;} setupPanel.setVisibility(View.GONE); errorPanel.setVisibility(View.GONE); webView.setVisibility(View.VISIBLE); webView.loadUrl(currentServer+"login.php"); }
    private void showSetup(boolean keepExisting) { webView.stopLoading(); webView.setVisibility(View.GONE); errorPanel.setVisibility(View.GONE); setupPanel.setVisibility(View.VISIBLE); if(keepExisting&&currentServer!=null)serverUrlInput.setText(currentServer); else if(serverUrlInput.getText().length()==0)serverUrlInput.setText(currentServer==null?"":currentServer); serverUrlInput.requestFocus(); }
    private void showConnectionError(String message) { errorText.setText(message); webView.setVisibility(View.GONE); setupPanel.setVisibility(View.GONE); errorPanel.setVisibility(View.VISIBLE); }
    private void showAppMenu() { String[] items={"Refresh","Dashboard","Change Chama Server","About"}; new AlertDialog.Builder(this).setTitle("Alpha Chama Member").setItems(items,(dialog,which)->{if(which==0)webView.reload();else if(which==1&&currentServer!=null)webView.loadUrl(currentServer+"index.php");else if(which==2)showSetup(true);else showAbout();}).show(); }
    private void showAbout() { new AlertDialog.Builder(this).setTitle("Alpha Chama Member").setMessage("Version 1.0.0\n\nMember mobile access for Alpha Chama Management System.\n\nDeveloped by Alpha Smart Systems and Computer Solutions.").setPositiveButton("OK",null).show(); }
    private String normalizeUrl(String url) { if(url==null)return"";url=url.trim();if(url.isEmpty())return"";return url.endsWith("/")?url:url+"/"; }
    private boolean isValidHttpUrl(String url) { try { URI uri=new URI(url);String scheme=uri.getScheme();return uri.getHost()!=null&&("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme)); } catch(URISyntaxException e){return false;} }
    @Override public void onBackPressed() { if(setupPanel.getVisibility()==View.VISIBLE||errorPanel.getVisibility()==View.VISIBLE){if(currentServer!=null&&!currentServer.isEmpty())connectToServer();else super.onBackPressed();}else if(webView.canGoBack())webView.goBack();else new AlertDialog.Builder(this).setMessage("Close Alpha Chama Member?").setNegativeButton("Cancel",null).setPositiveButton("Close",(d,w)->finish()).show(); }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) { super.onActivityResult(requestCode,resultCode,data);if(requestCode==FILE_CHOOSER_REQUEST&&filePathCallback!=null){Uri[] results=null;if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null)results=new Uri[]{data.getData()};filePathCallback.onReceiveValue(results);filePathCallback=null;} }
    @Override protected void onDestroy(){if(webView!=null){webView.loadUrl("about:blank");webView.stopLoading();webView.destroy();}super.onDestroy();}
}
