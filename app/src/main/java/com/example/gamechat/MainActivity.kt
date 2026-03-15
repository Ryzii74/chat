package com.example.gamechat

import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.example.gamechat.data.ChatServerClient
import com.example.gamechat.data.EncounterApiClient
import com.example.gamechat.data.NotificationHelper
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
import kotlin.math.abs

class MainActivity : AppCompatActivity(), EncounterAuthFragment.Host, EngineFragment.Host {
    private companion object {
        const val TAG = "MainActivityAuth"
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var gestureDetector: GestureDetector
    private var isUnlocked = false
    private var currentScreenMenuId: Int? = null
    
    // Типы анимации переходов
    enum class TransitionAnimation {
        NONE,
        SLIDE_LEFT_TO_RIGHT, // Свайп вправо - предыдущий экран
        SLIDE_RIGHT_TO_LEFT  // Свайп влево - следующий экран
    }
    
    // Порядок экранов для навигации свайпом
    private val screenOrder = listOf(
        R.id.encounterAuthFragment,
        R.id.chatsFragment,
        R.id.solverFragment,
        R.id.engineFragment,
        R.id.engineNativeFragment,
        R.id.settingsFragment,
        R.id.serverSettingsFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        updateAdminMenuVisibility()
        
        // Инициализируем GestureDetector для свайпов
        gestureDetector = GestureDetector(this, SwipeGestureListener())
        
        // Создаем каналы уведомлений
        NotificationHelper.createNotificationChannels(this)
        
        // Загружаем настройки с сервера при запуске
        loadServerSettingsOnStartup()

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
        Log.d(
            TAG,
            "Encounter authorized: login='${info.login}', site='${info.site}', userId='${info.userId.orEmpty()}'"
        )
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
        openScreen(fragment, title, screenMenuId, TransitionAnimation.NONE)
    }
    
    fun openScreen(fragment: Fragment, title: String, screenMenuId: Int?, animation: TransitionAnimation) {
        currentScreenMenuId = screenMenuId
        supportActionBar?.title = title
        
        val transaction = supportFragmentManager.beginTransaction()
        
        // Применяем анимацию в зависимости от типа перехода
        when (animation) {
            TransitionAnimation.SLIDE_LEFT_TO_RIGHT -> {
                transaction.setCustomAnimations(
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
                )
            }
            TransitionAnimation.SLIDE_RIGHT_TO_LEFT -> {
                transaction.setCustomAnimations(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                )
            }
            TransitionAnimation.NONE -> {
                // Без анимации
            }
        }
        
        transaction
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
        Log.d(TAG, "Checking app access: nick='${nick.trim()}', server='$serverUrl'")
        Thread {
            val accessResult = ChatServerClient.checkAppAccess(serverUrl, nick)
            runOnUiThread {
                val allowed = accessResult.getOrNull() ?: false
                val error = accessResult.exceptionOrNull()
                Log.d(
                    TAG,
                    "App access result: allowed=$allowed, error='${error?.message.orEmpty()}'"
                )
                if (allowed) {
                    Log.d(TAG, "Access granted, unlocking app UI")
                    unlockAppUi()
                } else {
                    Log.w(TAG, "Access denied, opening NotAvailable screen")
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
    
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // Обрабатываем свайпы только если приложение разблокировано и drawer закрыт
        if (isUnlocked && ev != null && !drawerLayout.isDrawerOpen(GravityCompat.START)) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }
    
    private inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val minSwipeDistance = 100
        private val maxSwipeOffPath = 250
        private val minSwipeVelocity = 200
        
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null || !isUnlocked || currentScreenMenuId == null) return false
            
            // Проверяем что это горизонтальный свайп
            if (abs(e1.y - e2.y) > maxSwipeOffPath) return false
            if (abs(velocityX) < minSwipeVelocity) return false
            if (abs(e1.x - e2.x) < minSwipeDistance) return false
            
            // Определяем направление свайпа
            if (e1.x - e2.x > 0) {
                // Свайп влево - следующий экран
                navigateToNextScreen()
            } else {
                // Свайп вправо - предыдущий экран
                navigateToPreviousScreen()
            }
            
            return true
        }
    }
    
    private fun navigateToNextScreen() {
        val currentIndex = getAvailableScreens().indexOf(currentScreenMenuId)
        if (currentIndex >= 0 && currentIndex < getAvailableScreens().size - 1) {
            val nextScreenId = getAvailableScreens()[currentIndex + 1]
            navigateToScreen(nextScreenId, TransitionAnimation.SLIDE_RIGHT_TO_LEFT)
        }
    }
    
    private fun navigateToPreviousScreen() {
        val currentIndex = getAvailableScreens().indexOf(currentScreenMenuId)
        if (currentIndex > 0) {
            val previousScreenId = getAvailableScreens()[currentIndex - 1]
            navigateToScreen(previousScreenId, TransitionAnimation.SLIDE_LEFT_TO_RIGHT)
        }
    }
    
    private fun getAvailableScreens(): List<Int> {
        return screenOrder.filter { screenId ->
            // Исключаем serverSettingsFragment если пользователь не админ
            if (screenId == R.id.serverSettingsFragment) {
                UserPreferences.isAdmin(this)
            } else {
                true
            }
        }
    }
    
    private fun navigateToScreen(screenId: Int, animation: TransitionAnimation = TransitionAnimation.NONE) {
        when (screenId) {
            R.id.encounterAuthFragment -> {
                navView.setCheckedItem(R.id.encounterAuthFragment)
                openScreen(
                    EncounterAuthFragment.newInstance(showSavedInfo = true, focusPassword = false),
                    getString(R.string.menu_encounter_auth),
                    R.id.encounterAuthFragment,
                    animation
                )
            }
            R.id.chatsFragment -> {
                navView.setCheckedItem(R.id.chatsFragment)
                openScreen(ChatsFragment(), getString(R.string.menu_chats), R.id.chatsFragment, animation)
            }
            R.id.solverFragment -> {
                navView.setCheckedItem(R.id.solverFragment)
                openScreen(SolverFragment(), getString(R.string.menu_solver), R.id.solverFragment, animation)
            }
            R.id.engineFragment -> {
                navView.setCheckedItem(R.id.engineFragment)
                openScreen(EngineFragment(), getString(R.string.menu_engine), R.id.engineFragment, animation)
            }
            R.id.engineNativeFragment -> {
                navView.setCheckedItem(R.id.engineNativeFragment)
                openScreen(
                    EngineNativeFragment(),
                    getString(R.string.menu_engine_native),
                    R.id.engineNativeFragment,
                    animation
                )
            }
            R.id.settingsFragment -> {
                navView.setCheckedItem(R.id.settingsFragment)
                openScreen(SettingsFragment(), getString(R.string.menu_settings), R.id.settingsFragment, animation)
            }
            R.id.serverSettingsFragment -> {
                if (UserPreferences.isAdmin(this)) {
                    navView.setCheckedItem(R.id.serverSettingsFragment)
                    openScreen(
                        ServerSettingsFragment(),
                        getString(R.string.menu_server_settings),
                        R.id.serverSettingsFragment,
                        animation
                    )
                }
            }
        }
    }
    
    private fun loadServerSettingsOnStartup() {
        // Загружаем настройки с сервера в фоновом режиме
        Thread {
            try {
                val serverUrl = UserPreferences.getServerUrl(this)
                if (serverUrl.isNotBlank()) {
                    UserPreferences.syncGameIdFromServer(this)
                }
            } catch (e: Exception) {
                // Игнорируем ошибки при загрузке настроек при старте
                e.printStackTrace()
            }
        }.start()
    }
}
