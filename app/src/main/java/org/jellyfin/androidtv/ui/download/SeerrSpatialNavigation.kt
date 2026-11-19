package org.jellyfin.androidtv.ui.download

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import timber.log.Timber

/**
 * Loads and drives the Seerr spatial-navigation script (D-pad highlight + activate).
 * Script API: `window.__seerrNav('up'|'down'|'left'|'right'|'enter'|'back'|'pick'|...)`.
 */
class SeerrSpatialNavigation(
	private val context: Context,
) {
	private val script: String? by lazy { loadAsset() }

	fun inject(webView: WebView) {
		forceDesktopViewport(webView)
		val js = script
		if (js.isNullOrBlank()) {
			Timber.e("Seerr spatnav.js missing from assets")
			return
		}
		webView.evaluateJavascript(js, null)
	}

	fun move(webView: WebView, direction: String) {
		webView.evaluateJavascript("window.__seerrNav&&window.__seerrNav('$direction')", null)
	}

	fun enter(webView: WebView) {
		webView.evaluateJavascript("window.__seerrNav&&window.__seerrNav('enter')") { result ->
			if (result?.contains("input") == true) {
				showKeyboard(webView)
			}
		}
	}

	fun openSearch(webView: WebView) {
		webView.evaluateJavascript("(window.__seerrNav&&window.__seerrNav('search'))||''") { result ->
			if (result?.contains("input") == true) {
				showKeyboard(webView)
			}
		}
	}

	/**
	 * @param onExit called when the page says BACK should leave Seerr (sidebar edge / home).
	 */
	fun back(webView: WebView, onExit: () -> Unit) {
		webView.evaluateJavascript("(window.__seerrNav&&window.__seerrNav('back'))||''") { result ->
			val value = result.orEmpty()
			when {
				value.contains("exit") -> onExit()
				value.contains("goback") || value.contains("sidebar") -> Unit
				webView.canGoBack() -> webView.goBack()
				else -> onExit()
			}
		}
	}

	fun dismissKeyboard(webView: WebView) {
		val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
		imm?.hideSoftInputFromWindow(webView.windowToken, 0)
		webView.requestFocus()
		webView.evaluateJavascript(
			"(function(){try{if(document.activeElement&&document.activeElement.blur)document.activeElement.blur();}" +
				"catch(e){}window.__seerrNav&&window.__seerrNav('pick');})()",
			null,
		)
	}

	fun isImeAcceptingText(webView: WebView): Boolean {
		val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
			?: return false
		return imm.isActive(webView) && imm.isAcceptingText
	}

	private fun showKeyboard(webView: WebView) {
		webView.requestFocus()
		val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
			?: return
		webView.evaluateJavascript("window.__seerrFocusInput&&window.__seerrFocusInput()") {
			imm.restartInput(webView)
			imm.showSoftInput(webView, 0)
		}
		webView.postDelayed({
			if (!imm.isActive(webView) || !imm.isAcceptingText) {
				imm.showSoftInput(webView, 0)
			}
		}, 150)
	}

	private fun forceDesktopViewport(webView: WebView) {
		// Seerr's mobile layout is awkward on TV; force a desktop CSS width.
		val js = """
			(function(){
			  function v(){
			    var m=document.querySelector('meta[name=viewport]');
			    if(!m){
			      m=document.createElement('meta');
			      m.setAttribute('name','viewport');
			      (document.head||document.documentElement).appendChild(m);
			    }
			    var w='width=$DESKTOP_CSS_WIDTH, user-scalable=no';
			    if(m.getAttribute('content')!==w)m.setAttribute('content',w);
			  }
			  v();
			  try{
			    new MutationObserver(v).observe(document.head||document.documentElement,{childList:true,subtree:true,attributes:true});
			  }catch(e){}
			})();
		""".trimIndent()
		webView.evaluateJavascript(js, null)
	}

	private fun loadAsset(): String? = try {
		context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
	} catch (e: Exception) {
		Timber.e(e, "Failed to read %s", ASSET_PATH)
		null
	}

	companion object {
		private const val ASSET_PATH = "seerr/spatnav.js"
		private const val DESKTOP_CSS_WIDTH = 1280
	}
}
