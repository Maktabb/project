package com.msalem.quote;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 7001;
    private WebView web;
    private WebView printView;
    private ValueCallback<Uri[]> fileCallback;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 21) getWindow().setStatusBarColor(Color.parseColor("#0B1F3A"));
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setTextZoom(100);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url.startsWith("file:") || url.startsWith("about:") || url.startsWith("data:") || url.startsWith("blob:")) return false;
                openExternal(url);
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try { startActivityForResult(Intent.createChooser(i, "اختاري ملف"), REQ_FILE); }
                catch (ActivityNotFoundException e) { fileCallback = null; return false; }
                return true;
            }
        });
        if (b != null) web.restoreState(b); else web.loadUrl("file:///android_asset/index.html");
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE) {
            Uri[] r = null;
            if (res == RESULT_OK && data != null && data.getData() != null) r = new Uri[]{data.getData()};
            if (fileCallback != null) fileCallback.onReceiveValue(r);
            fileCallback = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override public void onBackPressed() {
        web.evaluateJavascript("(window.__appBack&&window.__appBack())?'1':'0'", new ValueCallback<String>() {
            @Override public void onReceiveValue(String v) {
                if (v == null || !v.contains("1")) MainActivity.super.onBackPressed();
            }
        });
    }

    void toast(final String m) {
        runOnUiThread(new Runnable() { public void run() { Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show(); } });
    }

    void openExternal(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { toast("مفيش تطبيق يفتح الرابط ده"); }
    }

    static String safeName(String n) {
        if (n == null || n.trim().isEmpty()) n = "file";
        return n.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    static String mimeFor(String name) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".doc")) return "application/msword";
        if (n.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html";
        if (n.endsWith(".zip")) return "application/zip";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    static String waPhone(String p) {
        if (p == null) return "";
        String d = p.replaceAll("\\D", "");
        if (d.startsWith("00")) d = d.substring(2);
        else if (d.startsWith("0")) d = "2" + d;
        else if (d.length() > 0 && !d.startsWith("20")) d = "2" + d;
        return d;
    }

    boolean installed(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }

    class Bridge {
        @JavascriptInterface public boolean isApp() { return true; }

        @JavascriptInterface public void saveFile(String b64, String name, String mime) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                name = safeName(name);
                if (mime != null && mime.contains(";")) mime = mime.substring(0, mime.indexOf(";")).trim();
                if (mime == null || mime.isEmpty() || mime.equals("application/octet-stream")) mime = mimeFor(name);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put("_display_name", name);
                    v.put("mime_type", mime);
                    v.put("relative_path", Environment.DIRECTORY_DOWNLOADS + "/MahmoudSalem");
                    Uri uri = getContentResolver().insert(Uri.parse("content://media/external/downloads"), v);
                    if (uri == null) throw new Exception("insert failed");
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    os.write(data); os.close();
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MahmoudSalem");
                    dir.mkdirs();
                    FileOutputStream fo = new FileOutputStream(new File(dir, name));
                    fo.write(data); fo.close();
                }
                toast("✓ اتحفظ في التنزيلات (Download/MahmoudSalem):\n" + name);
            } catch (Exception e) {
                toast("تعذّر الحفظ: " + e.getMessage());
            }
        }

        @JavascriptInterface public void shareFile(String b64, String name, String mime, final String text, String phone) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                name = safeName(name);
                File f = new File(FilesProvider.shareDir(MainActivity.this), name);
                FileOutputStream fo = new FileOutputStream(f); fo.write(data); fo.close();
                final Uri uri = Uri.parse("content://com.msalem.quote.files/share/" + Uri.encode(name));
                final String type = (mime == null || mime.isEmpty()) ? mimeFor(name) : (mime.contains(";") ? mime.substring(0, mime.indexOf(";")).trim() : mime);
                final String wa = waPhone(phone);
                runOnUiThread(new Runnable() { public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType(type);
                    i.putExtra(Intent.EXTRA_STREAM, uri);
                    if (text != null && !text.isEmpty()) i.putExtra(Intent.EXTRA_TEXT, text);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    String pkg = installed("com.whatsapp") ? "com.whatsapp" : (installed("com.whatsapp.w4b") ? "com.whatsapp.w4b" : null);
                    if (pkg != null && !wa.isEmpty()) {
                        Intent w = new Intent(i);
                        w.setPackage(pkg);
                        w.putExtra("jid", wa + "@s.whatsapp.net");
                        try { startActivity(w); return; } catch (Exception e) { /* fall through to chooser */ }
                    }
                    try { startActivity(Intent.createChooser(i, "مشاركة الملف")); }
                    catch (Exception e) { toast("مفيش تطبيق للمشاركة"); }
                }});
            } catch (Exception e) {
                toast("تعذّرت المشاركة: " + e.getMessage());
            }
        }

        @JavascriptInterface public void shareText(final String text) {
            runOnUiThread(new Runnable() { public void run() {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                try { startActivity(Intent.createChooser(i, "مشاركة")); } catch (Exception e) { toast("مفيش تطبيق للمشاركة"); }
            }});
        }

        @JavascriptInterface public void openUrl(final String url) {
            runOnUiThread(new Runnable() { public void run() { openExternal(url); } });
        }

        @JavascriptInterface public void printHtml(final String html, final String title) {
            runOnUiThread(new Runnable() { public void run() {
                final String jobName = (title == null || title.isEmpty()) ? "مشروع تأسيس" : title;
                printView = new WebView(MainActivity.this);
                printView.getSettings().setJavaScriptEnabled(false);
                printView.getSettings().setAllowFileAccess(true);
                printView.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(final WebView v, String url) {
                        v.postDelayed(new Runnable() { public void run() {
                            PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                            PrintAttributes attrs = new PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build();
                            pm.print(jobName, v.createPrintDocumentAdapter(jobName), attrs);
                        }}, 700);
                    }
                });
                printView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
            }});
        }
    }
}
