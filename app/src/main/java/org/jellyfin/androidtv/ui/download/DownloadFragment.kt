package org.jellyfin.androidtv.ui.download

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.auth.pin.UserPinRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.compose.koinInject

/**
 * PIN-gated Seerr embed. Kids / accounts without a PIN should never land here;
 * the toolbar hides the entry point, and this fragment redirects Home if opened anyway.
 */
class DownloadFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		val sessionRepository = koinInject<SessionRepository>()
		val userPinRepository = koinInject<UserPinRepository>()
		val navigationRepository = koinInject<NavigationRepository>()
		val session by sessionRepository.currentSession.collectAsState()

		val pinRequired = remember(session) {
			val current = session ?: return@remember false
			userPinRepository.isPinRequired(current.serverId, current.userId)
		}

		LaunchedEffect(pinRequired) {
			if (!pinRequired) {
				navigationRepository.navigate(Destinations.home, replace = true)
			}
		}

		Column(modifier = Modifier.fillMaxSize()) {
			MainToolbar(MainToolbarActiveButton.Download)

			if (pinRequired) {
				SeerrWebView(
					modifier = Modifier
						.weight(1f)
						.fillMaxSize(),
					onExit = {
						navigationRepository.navigate(Destinations.home, replace = true)
					},
				)
			}
		}
	}
}
