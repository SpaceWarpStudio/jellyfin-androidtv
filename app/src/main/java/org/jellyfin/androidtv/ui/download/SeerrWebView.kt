package org.jellyfin.androidtv.ui.download

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.jellyfin.androidtv.ui.base.CircularProgressIndicator
import org.jellyfin.androidtv.ui.base.Text
import timber.log.Timber
import java.net.URI

private enum class SeerrLoadState {
	Loading,
	Ready,
	Error,
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SeerrWebView(
	url: String = SeerrConfig.BASE_URL,
	modifier: Modifier = Modifier,
	onExit: () -> Unit = {},
) {
	val context = LocalContext.current
	val spatialNav = remember(context) { SeerrSpatialNavigation(context) }
	var webViewRef by remember { mutableStateOf<SeerrFocusWebView?>(null) }
	var loadState by remember { mutableStateOf(SeerrLoadState.Loading) }
	var errorMessage by remember { mutableStateOf<String?>(null) }
	val focusRequester = remember { FocusRequester() }

	BackHandler {
		val webView = webViewRef
		when {
			webView == null -> onExit()
			spatialNav.isImeAcceptingText(webView) -> spatialNav.dismissKeyboard(webView)
			else -> spatialNav.back(webView, onExit = onExit)
		}
	}

	LaunchedEffect(loadState) {
		if (loadState == SeerrLoadState.Ready) {
			webViewRef?.requestFocus()
			runCatching { focusRequester.requestFocus() }
		}
	}

	Box(modifier = modifier.fillMaxSize()) {
		AndroidView(
			modifier = Modifier
				.fillMaxSize()
				.focusRequester(focusRequester)
				.focusable(),
			factory = { ctx ->
				SeerrFocusWebView(ctx).apply {
					layoutParams = ViewGroup.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT,
						ViewGroup.LayoutParams.MATCH_PARENT,
					)
					isFocusable = true
					isFocusableInTouchMode = true
					descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
					setBackgroundColor(Color.parseColor("#111827"))

					CookieManager.getInstance().let { cookies ->
						cookies.setAcceptCookie(true)
						cookies.setAcceptThirdPartyCookies(this, true)
					}

					settings.apply {
						javaScriptEnabled = true
						domStorageEnabled = true
						mediaPlaybackRequiresUserGesture = false
						mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
						cacheMode = WebSettings.LOAD_DEFAULT
						useWideViewPort = true
						loadWithOverviewMode = true
						builtInZoomControls = false
						setSupportZoom(false)
					}

					webChromeClient = WebChromeClient()
					webViewClient = object : WebViewClient() {
						override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
							loadState = SeerrLoadState.Loading
							errorMessage = null
						}

						override fun onPageFinished(view: WebView?, pageUrl: String?) {
							if (loadState != SeerrLoadState.Error) {
								loadState = SeerrLoadState.Ready
							}
							view?.let {
								spatialNav.inject(it)
								it.requestFocus()
							}
						}

						override fun onReceivedError(
							view: WebView?,
							request: WebResourceRequest?,
							error: WebResourceError?,
						) {
							if (request?.isForMainFrame != true) return
							loadState = SeerrLoadState.Error
							errorMessage = error?.description?.toString()
								?: "Failed to load Seerr"
							Timber.w("Seerr WebView error: %s", errorMessage)
						}

						override fun onReceivedSslError(
							view: WebView?,
							handler: SslErrorHandler?,
							error: SslError?,
						) {
							val host = error?.url?.let { runCatching { URI(it).host }.getOrNull() }
							if (host == SeerrConfig.TRUSTED_HOST) {
								Timber.i("Accepting self-signed TLS for Seerr host %s", host)
								handler?.proceed()
							} else {
								Timber.w("Rejecting SSL error for unexpected host %s", host)
								handler?.cancel()
								loadState = SeerrLoadState.Error
								errorMessage = "Secure connection failed"
							}
						}
					}

					onDispatchKey = { event ->
						handleSeerrKey(
							webView = this,
							spatialNav = spatialNav,
							event = event,
						)
					}

					webViewRef = this
					loadUrl(url)
				}
			},
			update = { webView ->
				webViewRef = webView
				webView.onDispatchKey = { event ->
					handleSeerrKey(
						webView = webView,
						spatialNav = spatialNav,
						event = event,
					)
				}
				if (webView.url.isNullOrBlank()) {
					webView.loadUrl(url)
				}
			},
		)

		when (loadState) {
			SeerrLoadState.Loading -> {
				CircularProgressIndicator(
					modifier = Modifier
						.size(48.dp)
						.align(Alignment.Center),
				)
			}

			SeerrLoadState.Error -> {
				Text(
					text = errorMessage ?: "Failed to load Seerr",
					modifier = Modifier.align(Alignment.Center),
				)
			}

			SeerrLoadState.Ready -> Unit
		}
	}
}

private fun handleSeerrKey(
	webView: SeerrFocusWebView,
	spatialNav: SeerrSpatialNavigation,
	event: KeyEvent,
): Boolean {
	val keyCode = event.keyCode
	val action = event.action

	if (spatialNav.isImeAcceptingText(webView)) {
		return when (keyCode) {
			KeyEvent.KEYCODE_BACK -> {
				if (action == KeyEvent.ACTION_UP) spatialNav.dismissKeyboard(webView)
				true
			}
			KeyEvent.KEYCODE_DPAD_UP,
			KeyEvent.KEYCODE_DPAD_DOWN,
			KeyEvent.KEYCODE_DPAD_LEFT,
			KeyEvent.KEYCODE_DPAD_RIGHT,
			KeyEvent.KEYCODE_DPAD_CENTER,
			KeyEvent.KEYCODE_ENTER,
			KeyEvent.KEYCODE_NUMPAD_ENTER,
			KeyEvent.KEYCODE_BUTTON_A,
			-> false
			else -> false
		}
	}

	val down = action == KeyEvent.ACTION_DOWN && event.repeatCount == 0

	return when (keyCode) {
		KeyEvent.KEYCODE_DPAD_LEFT -> {
			if (down) spatialNav.move(webView, "left")
			true
		}
		KeyEvent.KEYCODE_DPAD_RIGHT -> {
			if (down) spatialNav.move(webView, "right")
			true
		}
		KeyEvent.KEYCODE_DPAD_UP -> {
			if (down) spatialNav.move(webView, "up")
			true
		}
		KeyEvent.KEYCODE_DPAD_DOWN -> {
			if (down) spatialNav.move(webView, "down")
			true
		}
		KeyEvent.KEYCODE_DPAD_CENTER,
		KeyEvent.KEYCODE_ENTER,
		KeyEvent.KEYCODE_NUMPAD_ENTER,
		KeyEvent.KEYCODE_BUTTON_A,
		-> {
			if (down) spatialNav.enter(webView)
			true
		}
		KeyEvent.KEYCODE_SEARCH -> {
			if (down) spatialNav.openSearch(webView)
			true
		}
		else -> false
	}
}
