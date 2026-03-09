package com.example.gamechat

import android.os.Bundle
import android.view.Menu
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
    private var currentScreenMenuId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        updateAdminMenuVisibility()

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
                R.id.encounterAuthFragment -> {
                    openScreen(
                        EncounterAuthFragment.newInstance(showSavedInfo = true, focusPassword = false),
                        getString(R.string.menu_encounter_auth),
                        R.id.encounterAuthFragment
                    )
                    true
                }

                R.id.chatsFragment -> {
                    openScreen(ChatsFragment(), getString(R.string.menu_chats), R.id.chatsFragment)
                    true
                }

                R.id.solverFragment -> {
                    openScreen(SolverFragment(), getString(R.string.menu_solver), R.id.solverFragment)
                    true
                }

                R.id.engineFragment -> {
                    openScreen(EngineFragment(), getString(R.string.menu_engine), R.id.engineFragment)
                    true
                }

                R.id.engineNativeFragment -> {
                    openScreen(
                        EngineNativeFragment(),
                        getString(R.string.menu_engine_native),
                        R.id.engineNativeFragment
                    )
                    true
                }

                R.id.settingsFragment -> {
                    openScreen(SettingsFragment(), getString(R.string.menu_settings), R.id.settingsFragment)
                    true
                }

                R.id.serverSettingsFragment -> {
                    if (UserPreferences.isAdmin(this)) {
                        openScreen(
                            ServerSettingsFragment(),
                            getString(R.string.menu_server_settings),
                            R.id.serverSettingsFragment
                        )
                        true
                    } else {
                        false
                    }
                }

                else -> false
            }.also { handled ->
                if (handled) {
                    item.isChecked = true
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
            getString(R.string.menu_encounter_auth),
            R.id.encounterAuthFragment
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_solver_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val item = menu.findItem(R.id.toolbarSolverAutoToggleItem)
        val autoSwitch = item?.actionView as? SwitchCompat
        val showSwitch = isUnlocked && currentScreenMenuId == R.id.solverFragment
        item?.isVisible = showSwitch
        if (showSwitch && autoSwitch != null) {
            autoSwitch.setOnCheckedChangeListener(null)
            autoSwitch.isChecked = UserPreferences.isSolverAutoEnabled(this)
            autoSwitch.setOnCheckedChangeListener { _, isChecked ->
                UserPreferences.setSolverAutoEnabled(this, isChecked)
            }
        }
        return super.onPrepareOptionsMenu(menu)
    }

    fun openScreen(fragment: Fragment, title: String, screenMenuId: Int? = null) {
        currentScreenMenuId = screenMenuId
        supportActionBar?.title = title
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        invalidateOptionsMenu()
    }

    private fun updateAdminMenuVisibility() {
        navView.menu.findItem(R.id.serverSettingsFragment)?.isVisible = UserPreferences.isAdmin(this)
    }

    fun refreshAdminStateUi() {
        updateAdminMenuVisibility()
    }

    private fun showEncounterStartScreen() {
        lockAppUi()
        openScreen(
            EncounterAuthFragment.newInstance(showSavedInfo = false, focusPassword = true),
            getString(R.string.menu_encounter_auth),
            R.id.encounterAuthFragment
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
                    openScreen(
                        NotAvailableFragment(),
                        getString(R.string.app_not_available_title),
                        null
                    )
                }
            }
        }.start()
    }

    private fun lockAppUi() {
        isUnlocked = false
        currentScreenMenuId = null
        toolbar.visibility = View.GONE
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        navView.visibility = View.GONE
        drawerToggle.isDrawerIndicatorEnabled = false
        drawerToggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setHomeButtonEnabled(false)
        invalidateOptionsMenu()
    }

    private fun unlockAppUi() {
        isUnlocked = true
        toolbar.visibility = View.VISIBLE
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        navView.visibility = View.VISIBLE
        drawerToggle.isDrawerIndicatorEnabled = true
        drawerToggle.syncState()
        supportActionBar?.setHomeButtonEnabled(true)

        navView.setCheckedItem(R.id.engineFragment)
        openScreen(
            EngineFragment(),
            getString(R.string.menu_engine),
            R.id.engineFragment
        )
    }
}
