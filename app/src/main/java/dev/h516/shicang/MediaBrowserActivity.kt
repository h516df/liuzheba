package dev.h516.shicang

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.app.Activity
import android.content.Intent
import android.net.Uri
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
class MediaBrowserActivity:ComponentActivity() {
    private var web:WebView?=null
    private val browserBack=object:OnBackPressedCallback(false) {
        override fun handleOnBackPressed(){web?.goBack()}
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this,browserBack)
        val url=runCatching{MediaLinks.validUrl(intent.getStringExtra("url").orEmpty())}.getOrNull()
        if(url==null){finish();return}
        if(getSharedPreferences("device",0).getBoolean("appLock",false))window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(Button(this).apply{text="返回并解析当前网页";setOnClickListener{
            CookieManager.getInstance().flush()
            val current=web?.url.orEmpty()
            val target=runCatching{MediaLinks.validUrl(current)}.getOrNull()
            if(target!=null && Links.canResolveFromBrowser(target)) {
                setResult(Activity.RESULT_OK,Intent().setData(Uri.parse(target)))
            }
            finish()
        }})
        val label=TextView(this).apply{text=Links.host(url);setPadding(20,8,20,12)};root.addView(label)
        web=WebView(this).apply {
            settings.javaScriptEnabled=true;settings.domStorageEnabled=true
            settings.allowFileAccess=false;settings.allowContentAccess=false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
            webViewClient=object:WebViewClient(){
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean= request.url.scheme !in listOf("https")
                override fun onPageFinished(view:WebView,url:String){label.text=Links.host(url);CookieManager.getInstance().flush();browserBack.isEnabled=view.canGoBack()}
                override fun doUpdateVisitedHistory(view:WebView,url:String?,isReload:Boolean){browserBack.isEnabled=view.canGoBack()}
            }
            loadUrl(url)
        }
        root.addView(web,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
    }
    override fun onDestroy(){web?.destroy();web=null;super.onDestroy()}
}
