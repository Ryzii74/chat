package com.example.gamechat.ui

import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.gamechat.R
import com.example.gamechat.data.EncounterApiClient
import com.example.gamechat.data.EncounterUserAgentProvider
import com.example.gamechat.data.UserPreferences
import com.google.android.material.textfield.TextInputEditText

class EncounterAuthFragment : Fragment(R.layout.fragment_encounter_auth) {
    interface Host {
        fun onEncounterAuthorized(info: EncounterApiClient.UserInfo)
    }

    private var host: Host? = null
    private val showSavedInfo by lazy {
        arguments?.getBoolean(ARG_SHOW_SAVED_INFO, false) ?: false
    }
    private val focusPasswordOnStart by lazy {
        arguments?.getBoolean(ARG_FOCUS_PASSWORD, false) ?: false
    }

    companion object {
        private const val ARG_SHOW_SAVED_INFO = "show_saved_info"
        private const val ARG_FOCUS_PASSWORD = "focus_password"

        fun newInstance(showSavedInfo: Boolean, focusPassword: Boolean = false): EncounterAuthFragment {
            return EncounterAuthFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_SHOW_SAVED_INFO, showSavedInfo)
                    putBoolean(ARG_FOCUS_PASSWORD, focusPassword)
                }
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        host = context as? Host
    }

    override fun onDetach() {
        host = null
        super.onDetach()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val siteInput = view.findViewById<TextInputEditText>(R.id.encSiteInput)
        val loginInput = view.findViewById<TextInputEditText>(R.id.encLoginInput)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.encPasswordInput)
        val loginButton = view.findViewById<Button>(R.id.encLoginButton)
        val resultTitle = view.findViewById<TextView>(R.id.encResultTitle)
        val resultText = view.findViewById<TextView>(R.id.encResultText)

        val session = UserPreferences.getEncounterSession(requireContext())
        siteInput.setText(session.site.ifBlank { "https://world.en.cx" })
        loginInput.setText(session.login)
        if (showSavedInfo && session.login.isNotBlank()) {
            resultTitle.visibility = View.VISIBLE
            resultText.visibility = View.VISIBLE
            resultText.text = getString(
                R.string.enc_user_info_template,
                session.site.ifBlank { "https://world.en.cx" },
                session.login,
                session.userId.orEmpty().ifBlank { getString(R.string.enc_unknown) },
                session.guid.orEmpty().isNotBlank().toString(),
                session.stoken.orEmpty().isNotBlank().toString(),
                session.atoken.orEmpty().isNotBlank().toString()
            )
        } else {
            resultTitle.visibility = View.GONE
            resultText.visibility = View.GONE
        }

        if (focusPasswordOnStart) {
            passwordInput.requestFocus()
            passwordInput.post {
                passwordInput.requestFocus()
                val imm = requireContext().getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(passwordInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        loginButton.setOnClickListener {
            val site = siteInput.text?.toString().orEmpty()
            val login = loginInput.text?.toString().orEmpty()
            val password = passwordInput.text?.toString().orEmpty()

            if (login.isBlank() || password.isBlank()) {
                Toast.makeText(requireContext(), R.string.enc_fill_credentials, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            val defaultWebViewUa = EncounterUserAgentProvider.get(requireContext())
            Thread {
                val result = EncounterApiClient.login(site, login, password, userAgent = defaultWebViewUa)
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    loginButton.isEnabled = true

                    result.onSuccess { info ->
                        resultTitle.visibility = View.VISIBLE
                        resultText.visibility = View.VISIBLE
                        resultText.text = getString(
                            R.string.enc_user_info_template,
                            info.site,
                            info.login,
                            info.userId ?: getString(R.string.enc_unknown),
                            info.guid.isNullOrBlank().not().toString(),
                            info.stoken.isNullOrBlank().not().toString(),
                            info.atoken.isNullOrBlank().not().toString()
                        )
                        host?.onEncounterAuthorized(info)
                    }.onFailure { error ->
                        resultTitle.visibility = View.GONE
                        resultText.visibility = View.GONE
                        Toast.makeText(
                            requireContext(),
                            getString(
                                R.string.enc_auth_error,
                                error.message ?: getString(R.string.unknown_error)
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.start()
        }
    }
}
