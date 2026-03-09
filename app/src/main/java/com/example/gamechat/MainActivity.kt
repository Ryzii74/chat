package com.example.gamechat

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.example.gamechat.data.ChatServerClient
import com.example.gamechat.data.EncounterApiClient
import com.example.gamechat.data.UserPreferences
import com.example.gamechat.ui.ChatsFragment
import com.example.gamechat.ui.EncounterAuthFragment
import com.example.gamechat.ui.EngineFragment
import com.example.gamechat.ui.EngineNativeFragment
import com.example.gamechat.ui.NotAvailableFragment
import com.example.gamechat.ui.ServerSettingsFragment
import com.example.gamechat.ui.SettingsFragment
import com.example.gamechat.ui.SolverFragment
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity(), EncounterAuthFragment.Host, EngineFragment.Host {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private var isUnlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        updateAdminMenuVisibility()
        setupSolverAutoToggle()

        drawerToggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        )
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        navView.setNavigationItemSelectedListener { item ->
            if (!isUnlocked) return@setNavigationItemSelectedListener false

            when (item.itemId) {
                R.id.solverAutoToggleItem -> {
                    toggleSolverAuto()
                    true
                }

                R.id.encounterAuthFragment -> {
                    openScreen(
                        EncounterAuthFragment.newInstance(showSavedInfo = true, focusPassword = false),
                        getString(R.string.menu_encounter_auth)
                    )
                    true
                }

                R.id.chatsFragment -> {
                    openScreen(ChatsFragment(), getString(R.string.menu_chats))
                    true
                }

                R.id.solverFragment -> {
                    openScreen(SolverFragment(), getString(R.string.menu_solver))
                    true
                }

                R.id.engineFragment -> {
                    openScreen(EngineFragment(), getString(R.string.menu_engine))
                    true
                }

                R.id.engineNativeFragment -> {
                    openScreen(EngineNativeFragment(), getString(R.string.menu_engine_native))
                    true
                }

                R.id.settingsFragment -> {
                    openScreen(SettingsFragment(), getString(R.string.menu_settings))
                    true
                }

                R.id.serverSettingsFragment -> {
                    if (UserPreferences.isAdmin(this)) {
                        openScreen(ServerSettingsFragment(), getString(R.string.menu_server_settings))
                        true
                    } else {
                        false
                    }
                }

                else -> false
            }.also { handled ->
                if (handled) {
                    if (item.itemId != R.id.solverAutoToggleItem) {
                        item.isChecked = true
                    }
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
        }

        lockAppUi()
        showEncounterStartScreen()
    }

    override fun onResume() {
        super.onResume()
        updateAdminMenuVisibility()
    }

    override fun onEncounterAuthorized(info: EncounterApiClient.UserInfo) {
        UserPreferences.saveEncounterSession(
            context = this,
            site = info.site,
            login = info.login,
            userId = info.userId,
            guid = info.guid,
            stoken = info.stoken,
            atoken = info.atoken
        )
        checkEncounterAccessAndContinue(info.login)
    }

    override fun openEncounterAuthFromEngine() {
        navView.setCheckedItem(R.id.encounterAuthFragment)
        openScreen(
            EncounterAuthFragment.newInstance(showSavedInfo = true, focusPassword = true),
            getString(R.string.menu_encounter_auth)
        )
    }

    private fun openScreen(fragment: Fragment, title: String) {
        supportActionBar?.title = title
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun updateAdminMenuVisibility() {
        navView.menu.findItem(R.id.serverSettingsFragment)?.isVisible = UserPreferences.isAdmin(this)
    }

    private fun setupSolverAutoToggle() {
        val item = navView.menu.findItem(R.id.solverAutoToggleItem) ?: return
        val autoSwitch = item.actionView as? SwitchCompat ?: return
        autoSwitch.isChecked = UserPreferences.isSolverAutoEnabled(this)
        autoSwitch.setOnCheckedChangeListener { _, isChecked ->
            UserPreferences.setSolverAutoEnabled(this, isChecked)
        }
        item.actionView?.setOnClickListener {
            autoSwitch.isChecked = !autoSwitch.isChecked
        }
    }

    private fun toggleSolverAuto() {
        val item = navView.menu.findItem(R.id.solverAutoToggleItem) ?: return
        val autoSwitch = item.actionView as? SwitchCompat ?: return
        autoSwitch.isChecked = !autoSwitch.isChecked
    }

    private fun showEncounterStartScreen() {
        lockAppUi()
        openScreen(
            EncounterAuthFragment.newInstance(showSavedInfo = false, focusPassword = true),
            getString(R.string.menu_encounter_auth)
        )
    }

    private fun checkEncounterAccessAndContinue(nick: String) {
        val serverUrl = UserPreferences.getServerUrl(this)
        Thread {
            val accessResult = ChatServerClient.checkAppAccess(serverUrl, nick)
            runOnUiThread {
                val allowed = accessResult.getOrNull() ?: false
                if (allowed) {
                    unlockAppUi()
                } else {
                    lockAppUi()
                    openScreen(NotAvailableFragment(), getString(R.string.app_not_available_title))
                }
            }
        }.start()
    }

    private fun lockAppUi() {
        isUnlocked = false
        toolbar.visibility = View.GONE
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        navView.visibility = View.GONE
        drawerToggle.isDrawerIndicatorEnabled = false
        drawerToggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setHomeButtonEnabled(false)
    }

    private fun unlockAppUi() {
        isUnlocked = true
        toolbar.visibility = View.VISIBLE
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        navView.visibility = View.VISIBLE
        drawerToggle.isDrawerIndicatorEnabled = true
        drawerToggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        navView.setCheckedItem(R.id.engineFragment)
        openScreen(
            EngineFragment(),
            getString(R.string.menu_engine)
        )
    }
}
