package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.fragment.compose.content
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ApiClientErrorLoginState
import org.jellyfin.androidtv.auth.model.AuthenticatedState
import org.jellyfin.androidtv.auth.model.AuthenticatingState
import org.jellyfin.androidtv.auth.model.InvalidPinState
import org.jellyfin.androidtv.auth.model.RequirePinState
import org.jellyfin.androidtv.auth.model.RequireSignInState
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.ServerUnavailableState
import org.jellyfin.androidtv.auth.model.ServerVersionNotSupported
import org.jellyfin.androidtv.auth.model.User
import org.jellyfin.androidtv.auth.pin.PinHasher
import org.jellyfin.androidtv.auth.pin.UserPinRepository
import org.jellyfin.androidtv.auth.repository.AuthenticationRepository
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.composable.modifier.overscan
import org.jellyfin.androidtv.ui.navigation.focus.FocusPersistManager
import org.jellyfin.androidtv.ui.navigation.focus.LocalFocusPersistManager
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.pin.PinPad
import org.jellyfin.androidtv.util.createBundle
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import java.util.UUID

class UserPinFragment : Fragment() {
	companion object {
		const val ARG_SERVER_ID = "server_id"
		const val ARG_USER_ID = "user_id"
	}

	private val serverRepository: ServerRepository by inject()

	private val serverId: UUID?
		get() = arguments?.getString(ARG_SERVER_ID)?.ifBlank { null }?.toUUIDOrNull()
	private val userId: UUID?
		get() = arguments?.getString(ARG_USER_ID)?.ifBlank { null }?.toUUIDOrNull()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		JellyfinTheme {
			// Startup screens are outside the settings NavHost that normally provides this.
			val focusPersistManager = remember { FocusPersistManager() }
			CompositionLocalProvider(LocalFocusPersistManager provides focusPersistManager) {
				UserPinScreen(
					serverId = serverId,
					userId = userId,
					onOpenPasswordLogin = ::openPasswordLogin,
					onCancel = ::cancelPinEntry,
					onMissingContext = ::showServerOrSelection,
				)
			}
		}
	}

	private fun openPasswordLogin(server: Server, user: User) {
		parentFragmentManager.commit {
			replace<UserLoginFragment>(
				R.id.content_view,
				null,
				createBundle {
					putString(UserLoginFragment.ARG_SERVER_ID, server.id.toString())
					putString(UserLoginFragment.ARG_USERNAME, user.name)
					putBoolean(UserLoginFragment.ARG_SKIP_QUICKCONNECT, true)
				}
			)
			addToBackStack(null)
		}
	}

	private fun cancelPinEntry() {
		if (!parentFragmentManager.popBackStackImmediate()) {
			showServerOrSelection()
		}
	}

	private fun showServerOrSelection() {
		val server = serverId?.let { id -> serverRepository.storedServers.value.find { it.id == id } }
		parentFragmentManager.commit {
			replace<StartupToolbarFragment>(R.id.content_view)
			if (server != null) {
				add<ServerFragment>(
					R.id.content_view,
					null,
					createBundle {
						putString(ServerFragment.ARG_SERVER_ID, server.id.toString())
					}
				)
			} else {
				add<SelectServerFragment>(R.id.content_view)
			}
		}
	}
}

@Composable
private fun UserPinScreen(
	serverId: UUID?,
	userId: UUID?,
	onOpenPasswordLogin: (Server, User) -> Unit,
	onCancel: () -> Unit,
	onMissingContext: () -> Unit,
) {
	val authenticationRepository = koinInject<AuthenticationRepository>()
	val serverRepository = koinInject<ServerRepository>()
	val serverUserRepository = koinInject<ServerUserRepository>()
	val userPinRepository = koinInject<UserPinRepository>()
	val backgroundService = koinInject<BackgroundService>()
	val scope = rememberCoroutineScope()

	val server = remember(serverId, serverRepository.storedServers.value) {
		serverId?.let { id -> serverRepository.storedServers.value.find { it.id == id } }
	}
	val user = remember(server, userId) {
		if (server == null || userId == null) null
		else serverUserRepository.getStoredServerUsers(server).find { it.id == userId }
	}

	LaunchedEffect(server, user) {
		if (server == null || user == null) {
			onMissingContext()
			return@LaunchedEffect
		}
		backgroundService.setBackground(server)
	}

	if (server == null || user == null) return

	var pin by remember { mutableStateOf("") }
	var statusMessage by remember { mutableStateOf<String?>(null) }
	var authJob by remember { mutableStateOf<Job?>(null) }
	val focusRequester = remember { FocusRequester() }
	val context = LocalContext.current
	val authenticatingLabel = stringResource(R.string.login_authenticating)
	val serverUnavailableLabel = stringResource(R.string.login_server_unavailable)

	fun tryAutoUnlock(candidate: String) {
		// Only attempt when length is a valid PIN size. Wrong values stay silent so length
		// is not advertised; only the exact correct PIN signs in.
		if (candidate.length !in PinHasher.MIN_LENGTH..PinHasher.MAX_LENGTH) return
		if (!userPinRepository.verifyPin(server.id, user.id, candidate)) return
		if (authJob?.isActive == true) return

		authJob = scope.launch {
			authenticationRepository.authenticateWithPin(server, user, candidate).collect { state ->
				when (state) {
					AuthenticatingState -> statusMessage = authenticatingLabel
					InvalidPinState,
					is RequirePinState -> {
						// Should be rare after local verify; stay silent and keep digits.
						statusMessage = null
					}

					RequireSignInState -> onOpenPasswordLogin(server, user)

					ServerUnavailableState,
					is ApiClientErrorLoginState -> statusMessage = serverUnavailableLabel

					is ServerVersionNotSupported -> {
						statusMessage = context.getString(
							R.string.server_issue_outdated_version,
							state.server.version,
							ServerRepository.recommendedServerVersion.toString(),
						)
					}

					AuthenticatedState -> statusMessage = null
				}
			}
		}
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(colorResource(R.color.black_transparent_light))
			.overscan(),
		contentAlignment = Alignment.Center,
	) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Text(
				text = stringResource(R.string.login_enter_pin),
				fontSize = 28.sp,
			)
			Text(
				text = stringResource(R.string.login_pin_for_user, user.name),
				fontSize = 16.sp,
			)

			PinPad(
				pin = pin,
				onPinChange = { next ->
					pin = next
					statusMessage = null
					tryAutoUnlock(next)
				},
				showPlaceholderWhenEmpty = false,
				initialFocusRequester = focusRequester,
			)

			if (statusMessage != null) {
				Text(text = statusMessage!!)
			}

			Row(
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				modifier = Modifier.padding(top = 8.dp),
			) {
				Button(
					onClick = { onOpenPasswordLogin(server, user) },
					modifier = Modifier.focusKey("pin_use_password"),
				) {
					Text(stringResource(R.string.action_use_password))
				}
				Button(
					onClick = onCancel,
					modifier = Modifier.focusKey("pin_cancel"),
				) {
					Text(stringResource(R.string.btn_cancel))
				}
			}
		}
	}

	LaunchedEffect(focusRequester) {
		focusRequester.requestFocus()
	}
}
