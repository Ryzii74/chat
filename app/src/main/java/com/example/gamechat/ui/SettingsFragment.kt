package com.example.gamechat.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.gamechat.R
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
            val success = UserPreferences.loginAsAdmin(requireContext(), pin)
            if (success) {
                adminPinInput.text?.clear()
                Toast.makeText(requireContext(), R.string.admin_login_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.admin_login_error, Toast.LENGTH_SHORT).show()
            }
            renderAdminState(
                adminStatusText = adminStatusText,
                adminPinLayout = adminPinLayout,
                adminLoginButton = adminLoginButton,
                adminLogoutButton = adminLogoutButton
            )
            activity?.recreate()
        }

        adminLogoutButton.setOnClickListener {
            UserPreferences.logoutAdmin(requireContext())
            Toast.makeText(requireContext(), R.string.admin_logout_success, Toast.LENGTH_SHORT).show()
            renderAdminState(
                adminStatusText = adminStatusText,
                adminPinLayout = adminPinLayout,
                adminLoginButton = adminLoginButton,
                adminLogoutButton = adminLogoutButton
            )
            activity?.recreate()
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
