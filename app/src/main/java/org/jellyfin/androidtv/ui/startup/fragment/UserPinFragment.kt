package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ApiClientErrorLoginState
import org.jellyfin.androidtv.auth.model.AuthenticatedState
import org.jellyfin.androidtv.auth.model.AuthenticatingState
import org.jellyfin.androidtv.auth.model.InvalidPinState
import org.jellyfin.androidtv.auth.model.RequirePinState
import org.jellyfin.androidtv.auth.model.RequireSignInState
import org.jellyfin.androidtv.auth.model.ServerUnavailableState
import org.jellyfin.androidtv.auth.model.ServerVersionNotSupported
import org.jellyfin.androidtv.auth.model.User
import org.jellyfin.androidtv.auth.repository.AuthenticationRepository
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.databinding.FragmentUserPinBinding
import org.jellyfin.androidtv.util.createBundle
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import java.util.UUID

class UserPinFragment : Fragment() {
	companion object {
		const val ARG_SERVER_ID = "server_id"
		const val ARG_USER_ID = "user_id"
	}

	private val authenticationRepository: AuthenticationRepository by inject()
	private val serverRepository: ServerRepository by inject()
	private val serverUserRepository: ServerUserRepository by inject()
	private val backgroundService: BackgroundService by inject()

	private var _binding: FragmentUserPinBinding? = null
	private val binding get() = _binding!!

	private val serverId: UUID?
		get() = arguments?.getString(ARG_SERVER_ID)?.ifBlank { null }?.toUUIDOrNull()
	private val userId: UUID?
		get() = arguments?.getString(ARG_USER_ID)?.ifBlank { null }?.toUUIDOrNull()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		_binding = FragmentUserPinBinding.inflate(inflater, container, false)

		binding.cancel.setOnClickListener {
			if (!parentFragmentManager.popBackStackImmediate()) {
				showServerOrSelection()
			}
		}

		binding.usePassword.setOnClickListener {
			val server = resolveServer()
			val user = resolveUser()
			if (server == null || user == null) return@setOnClickListener

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

		binding.confirm.setOnClickListener { submitPin() }
		binding.pin.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				submitPin()
				true
			} else {
				false
			}
		}

		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val server = resolveServer()
		val user = resolveUser()
		if (server == null || user == null) {
			showServerOrSelection()
			return
		}

		binding.subtitle.text = getString(R.string.login_pin_for_user, user.name)
		backgroundService.setBackground(server)
		binding.pin.requestFocus()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	private fun resolveServer() =
		serverId?.let { id -> serverRepository.storedServers.value.find { it.id == id } }

	private fun resolveUser(): User? {
		val server = resolveServer() ?: return null
		val id = userId ?: return null
		return serverUserRepository.getStoredServerUsers(server).find { it.id == id }
	}

	private fun submitPin() {
		val server = resolveServer() ?: return
		val user = resolveUser() ?: return
		val pin = binding.pin.text?.toString().orEmpty()

		if (pin.isBlank()) {
			binding.error.setText(R.string.login_pin_empty)
			return
		}

		lifecycleScope.launch {
			authenticationRepository.authenticateWithPin(server, user, pin).collect { state ->
				when (state) {
					AuthenticatingState -> binding.error.setText(R.string.login_authenticating)
					InvalidPinState,
					is RequirePinState -> {
						binding.error.setText(R.string.login_invalid_pin)
						binding.pin.text?.clear()
						binding.pin.requestFocus()
					}

					RequireSignInState -> {
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

					ServerUnavailableState,
					is ApiClientErrorLoginState -> binding.error.setText(R.string.login_server_unavailable)

					is ServerVersionNotSupported -> binding.error.setText(
						getString(
							R.string.server_issue_outdated_version,
							state.server.version,
							ServerRepository.recommendedServerVersion.toString()
						)
					)

					// Activity responds to the new session
					AuthenticatedState -> Unit
				}
			}
		}
	}

	private fun showServerOrSelection() {
		val server = resolveServer()
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
