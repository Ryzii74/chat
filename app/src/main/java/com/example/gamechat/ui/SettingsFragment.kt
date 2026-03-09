package com.example.gamechat.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.gamechat.R
import com.example.gamechat.data.ChatServerClient
import com.example.gamechat.data.UserPreferences
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adminStatusText = view.findViewById<TextView>(R.id.adminStatusText)
        val adminPinLayout = view.findViewById<TextInputLayout>(R.id.adminPinLayout)
        val adminPinInput = view.findViewById<TextInputEditText>(R.id.adminPinInput)
        val adminLoginButton = view.findViewById<Button>(R.id.adminLoginButton)
        val adminLogoutButton = view.findViewById<Button>(R.id.adminLogoutButton)

        renderAdminState(
            adminStatusText = adminStatusText,
            adminPinLayout = adminPinLayout,
            adminLoginButton = adminLoginButton,
            adminLogoutButton = adminLogoutButton
        )

        adminLoginButton.setOnClickListener {
            val pin = adminPinInput.text?.toString().orEmpty()
            val serverUrl = UserPreferences.getServerUrl(requireContext())
            adminLoginButton.isEnabled = false
            Thread {
                val result = ChatServerClient.loginAsAdmin(serverUrl, pin)
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    adminLoginButton.isEnabled = true

                    result.onSuccess { token ->
                        UserPreferences.saveAdminToken(requireContext(), token)
                        adminPinInput.text?.clear()
                        Toast.makeText(requireContext(), R.string.admin_login_success, Toast.LENGTH_SHORT).show()
                        renderAdminState(
                            adminStatusText = adminStatusText,
                            adminPinLayout = adminPinLayout,
                            adminLoginButton = adminLoginButton,
                            adminLogoutButton = adminLogoutButton
                        )
                        (activity as? com.example.gamechat.MainActivity)?.refreshAdminStateUi()
                    }.onFailure {
                        Toast.makeText(requireContext(), R.string.admin_login_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }

        adminLogoutButton.setOnClickListener {
            val serverUrl = UserPreferences.getServerUrl(requireContext())
            val adminToken = UserPreferences.getAdminToken(requireContext())
            adminLogoutButton.isEnabled = false
            Thread {
                ChatServerClient.logoutAdmin(serverUrl, adminToken)
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    adminLogoutButton.isEnabled = true
                    UserPreferences.logoutAdmin(requireContext())
                    Toast.makeText(requireContext(), R.string.admin_logout_success, Toast.LENGTH_SHORT).show()
                    renderAdminState(
                        adminStatusText = adminStatusText,
                        adminPinLayout = adminPinLayout,
                        adminLoginButton = adminLoginButton,
                        adminLogoutButton = adminLogoutButton
                    )
                    (activity as? com.example.gamechat.MainActivity)?.refreshAdminStateUi()
                }
            }.start()
        }
    }

    private fun renderAdminState(
        adminStatusText: TextView,
        adminPinLayout: TextInputLayout,
        adminLoginButton: Button,
        adminLogoutButton: Button
    ) {
        val isAdmin = UserPreferences.isAdmin(requireContext())
        if (isAdmin) {
            adminStatusText.setText(R.string.admin_status_on)
            adminPinLayout.visibility = View.GONE
            adminLoginButton.visibility = View.GONE
            adminLogoutButton.visibility = View.VISIBLE
        } else {
            adminStatusText.setText(R.string.admin_status_off)
            adminPinLayout.visibility = View.VISIBLE
            adminLoginButton.visibility = View.VISIBLE
            adminLogoutButton.visibility = View.GONE
        }
    }
}
