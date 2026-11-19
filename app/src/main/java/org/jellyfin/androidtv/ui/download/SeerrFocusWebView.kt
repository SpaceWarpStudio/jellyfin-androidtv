package org.jellyfin.androidtv.ui.download

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.webkit.WebView

/**
 * WebView that lets us handle TV remote keys before the engine's default
 * scrolling / focus behavior.
 */
class SeerrFocusWebView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : WebView(context, attrs) {
	var onDispatchKey: ((KeyEvent) -> Boolean)? = null

	override fun dispatchKeyEvent(event: KeyEvent): Boolean {
		if (onDispatchKey?.invoke(event) == true) return true
		return super.dispatchKeyEvent(event)
	}
}
