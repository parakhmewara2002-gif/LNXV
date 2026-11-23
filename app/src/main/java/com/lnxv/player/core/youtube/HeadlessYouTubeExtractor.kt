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
                val webView = WebView(context.applicationContext)

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

                webView.loadUrl("https://m.youtube.com/watch?v=$videoId")
            }
        }
    }
}
