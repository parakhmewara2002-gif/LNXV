package com.lnxv.player.core.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Headless background stream extractor.
 * Executes YouTube web player JavaScript in an off-screen WebView to resolve
 * genuine botguard-signed googlevideo playback URLs WITHOUT requiring any user login!
 */
object HeadlessYouTubeExtractor {

    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolveStream(context: Context, videoId: String): String? = withTimeoutOrNull(12000L) {
        suspendCancellableCoroutine { continuation ->
            mainHandler.post {
                var resumed = false
                val webView = try {
                    WebView(context)
                } catch (_: Exception) {
                    WebView(context.applicationContext)
                }

                fun finish(url: String?) {
                    if (!resumed) {
                        resumed = true
                        try {
                            webView.stopLoading()
                            webView.destroy()
                        } catch (_: Exception) {}
                        if (continuation.isActive) {
                            continuation.resume(url)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    mainHandler.post {
                        try {
                            webView.stopLoading()
                            webView.destroy()
                        } catch (_: Exception) {}
                    }
                }

                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    userAgentString = "Mozilla/5.0 (Linux; Android 11; TECNO CG7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: ""
                        if (reqUrl.contains("googlevideo.com/videoplayback") && reqUrl.contains("itag=")) {
                            finish(reqUrl)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Trigger video playback automatically
                        view?.evaluateJavascript(
                            """
                            (function() {
                                var v = document.querySelector('video');
                                if (v) {
                                    v.muted = true;
                                    v.play();
                                }
                            })();
                            """.trimIndent(),
                            null
                        )
                    }
                }

                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    </head>
                    <body style="margin:0;padding:0;background-color:#000;">
                    <iframe id="player" width="100%" height="100%" 
                        src="https://www.youtube.com/embed/$videoId?autoplay=1&enablejsapi=1&playsinline=1" 
                        frameborder="0" allow="autoplay; encrypted-media" allowfullscreen>
                    </iframe>
                    </body>
                    </html>
                """.trimIndent()
                webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
            }
        }
    }
}
