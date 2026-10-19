package dev.h516.shicang

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** A normal local browser session supplies site cookies to the on-device extractor. No JS bridge. */
class MediaBrowserActivity:Activity() {
    private var web:WebView?=null
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val url=runCatching{MediaLinks.validUrl(intent.getStringExtra("url").orEmpty())}.getOrNull()
        if(url==null){finish();return}
        if(getSharedPreferences("device",0).getBoolean("appLock",false))window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(Button(this).apply{text="返回留册 · 完成验证后重新解析";setOnClickListener{finish()}})
        val label=TextView(this).apply{text=Links.host(url);setPadding(20,8,20,12)};root.addView(label)
        web=WebView(this).apply {
            settings.javaScriptEnabled=true;settings.domStorageEnabled=true
            settings.allowFileAccess=false;settings.allowContentAccess=false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
            webViewClient=object:WebViewClient(){
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean= request.url.scheme !in listOf("https")
                override fun onPageFinished(view:WebView,url:String){label.text=Links.host(url);CookieManager.getInstance().flush()}
            }
            loadUrl(url)
        }
        root.addView(web,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
    }
    @Deprecated("Deprecated in Java") override fun onBackPressed(){if(web?.canGoBack()==true)web?.goBack() else super.onBackPressed()}
    override fun onDestroy(){web?.destroy();web=null;super.onDestroy()}
}
