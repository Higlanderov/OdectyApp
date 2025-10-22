package cz.davidfryda.odectyapp

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging

@ExperimentalBadgeUtils
class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    private var isUserMaster: Boolean? = null

    private var notificationBadge: BadgeDrawable? = null
    private var unreadListener: ListenerRegistration? = null
    private val db = Firebase.firestore

    private var notificationMenuItem: MenuItem? = null
    private var hasUnreadNotifications = false

    // NOVÉ: Reference na toolbar
    private lateinit var toolbar: Toolbar

    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val mainContentContainer: LinearLayout = view.findViewById(R.id.main_content_container)
            mainContentContainer.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navView: NavigationView = findViewById(R.id.nav_view)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.mainFragment,
                R.id.profileFragment,
                R.id.masterUserListFragment
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        setupDrawerNavigation(navView, drawerLayout)
        setupNotificationBadge()

        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerOpened(drawerView: View) { updateNavHeader(navView) }
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        updateNavHeader(navView)
        updateFcmToken()

        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d(TAG, "Navigace na destinaci: ${destination.label} (ID: ${destination.id})")
            updateUIBasedOnDestination(destination)
        }
    }

    private fun updateUIBasedOnDestination(destination: NavDestination) {
        val hideBellOnDestinations = setOf(
            R.id.splashFragment,
            R.id.loginFragment,
            R.id.registerFragment,
            R.id.userInfoFragment
        )
        val shouldHideBell = destination.id in hideBellOnDestinations

        notificationMenuItem?.isVisible = !shouldHideBell
        Log.d(TAG, "Viditelnost zvonečku: ${!shouldHideBell}")

        updateBadgeVisibility()

        val isMainLoggedInDestination = destination.id == R.id.mainFragment ||
                destination.id == R.id.masterUserListFragment

        if (isMainLoggedInDestination && !shouldHideBell) {
            Log.d(TAG, "Hlavní destinace -> kontroluji roli pro listener.")
            checkRoleAndSetupListenerIfNeeded()
        } else {
            Log.d(TAG, "Jiná destinace -> odpojuji listener.")
            unreadListener?.remove()
            updateBadgeVisibility()
        }
    }

    // OPRAVENO: Použití BadgeUtils.attachBadgeDrawable s postDelayed
    private fun updateBadgeVisibility() {
        val shouldShowBadge = (notificationMenuItem?.isVisible == true) && hasUnreadNotifications

        Log.d(TAG, "🎯 updateBadgeVisibility:")
        Log.d(TAG, "🎯   menuItem?.isVisible = ${notificationMenuItem?.isVisible}")
        Log.d(TAG, "🎯   hasUnreadNotifications = $hasUnreadNotifications")
        Log.d(TAG, "🎯   shouldShowBadge = $shouldShowBadge")

        notificationBadge?.isVisible = shouldShowBadge

        // KLÍČOVÁ ZMĚNA: Re-attach badge po změně viditelnosti
        if (shouldShowBadge) {
            toolbar.post {
                try {
                    notificationBadge?.let { badge ->
                        // Nejdřív odpojíme
                        BadgeUtils.detachBadgeDrawable(badge, toolbar, R.id.action_notifications)
                        // Pak znovu připojíme
                        BadgeUtils.attachBadgeDrawable(badge, toolbar, R.id.action_notifications)
                        Log.d(TAG, "✅ Badge znovu připojen")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Chyba při re-attach badge", e)
                }
            }
        }

        Log.d(TAG, "🎯   badge?.isVisible = ${notificationBadge?.isVisible}")
    }

    private fun checkRoleAndSetupListenerIfNeeded() {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "Uživatel není přihlášen.")
            unreadListener?.remove()
            hasUnreadNotifications = false
            updateBadgeVisibility()
            return
        }

        if (isUserMaster == true) {
            Log.d(TAG, "Role master známa, spouštím listener.")
            listenForUnreadNotifications(currentUser.uid)
            return
        }
        if (isUserMaster == false) {
            Log.d(TAG, "Role non-master známa, listener nespouštím.")
            unreadListener?.remove()
            hasUnreadNotifications = false
            updateBadgeVisibility()
            return
        }

        Log.d(TAG, "Role není známa, zjišťuji z Firestore.")
        Firebase.firestore.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                val isMaster = document.getString("role") == "master"
                isUserMaster = isMaster
                Log.d(TAG, "Role získána: isMaster = $isMaster")

                if (isMaster) {
                    Log.d(TAG, "Je master, spouštím listener.")
                    listenForUnreadNotifications(currentUser.uid)
                } else {
                    Log.d(TAG, "Není master, odpojuji listener.")
                    unreadListener?.remove()
                    hasUnreadNotifications = false
                    updateBadgeVisibility()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Chyba při získávání role.", e)
                isUserMaster = false
                unreadListener?.remove()
                hasUnreadNotifications = false
                updateBadgeVisibility()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        unreadListener?.remove()
        Log.d(TAG, "onDestroy: Listener odpojen.")
    }

    private fun setupNotificationBadge() {
        Log.d(TAG, "setupNotificationBadge voláno.")
        notificationBadge = BadgeDrawable.create(this).apply {
            isVisible = false
            backgroundColor = getColor(android.R.color.holo_red_dark)
            badgeTextColor = getColor(android.R.color.white)
        }
    }

    private fun listenForUnreadNotifications(masterUserId: String) {
        Log.d(TAG, "📬 Spouštím listener pro master ID: $masterUserId")
        unreadListener?.remove()
        unreadListener = db.collection("notifications").document(masterUserId).collection("items")
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Chyba při naslouchání notifikacím:", error)
                    hasUnreadNotifications = false
                    updateBadgeVisibility()
                    return@addSnapshotListener
                }

                val hasUnread = snapshots != null && !snapshots.isEmpty
                val count = snapshots?.size() ?: 0

                Log.d(TAG, "📬 Listener triggered: count=$count, hasUnread=$hasUnread")

                hasUnreadNotifications = hasUnread
                updateBadgeVisibility()
            }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        notificationMenuItem = menu.findItem(R.id.action_notifications)

        navController.currentDestination?.let { updateUIBasedOnDestination(it) }

        // OPRAVENO: Připojíme badge s post delay
        toolbar.post {
            notificationBadge?.let { badge ->
                Log.d(TAG, "🔗 Připojuji badge k toolbaru.")
                try {
                    BadgeUtils.attachBadgeDrawable(badge, toolbar, R.id.action_notifications)
                    Log.d(TAG, "✅ Badge úspěšně připojen")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Chyba při připojování badge", e)
                }
            }
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_notifications -> {
                navController.navigate(R.id.notificationListFragment)
                true
            }
            else -> item.onNavDestinationSelected(navController) || super.onOptionsItemSelected(item)
        }
    }

    private fun updateFcmToken() {
        val currentUser = Firebase.auth.currentUser ?: return
        Firebase.messaging.token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Načtení FCM tokenu selhalo", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            val userDocRef = Firebase.firestore.collection("users").document(currentUser.uid)
            userDocRef.set(mapOf("fcmToken" to token), SetOptions.merge())
                .addOnSuccessListener { Log.d(TAG, "FCM Token uložen.") }
                .addOnFailureListener { e -> Log.w(TAG, "Uložení FCM Tokenu selhalo", e) }
        }
    }

    private fun setupDrawerNavigation(navView: NavigationView, drawerLayout: DrawerLayout) {
        navView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.close()
            when (menuItem.itemId) {
                R.id.logout_action -> {
                    Firebase.auth.signOut()
                    isUserMaster = null
                    hasUnreadNotifications = false
                    Log.d(TAG, "Odhlášení.")
                    unreadListener?.remove()
                    updateBadgeVisibility()
                    val navOptions = NavOptions.Builder()
                        .setPopUpTo(navController.graph.findStartDestination().id, true)
                        .build()
                    navController.navigate(R.id.splashFragment, null, navOptions)
                    return@setNavigationItemSelectedListener true
                }
                R.id.mainFragment -> {
                    navigateHome(isUserMaster ?: false)
                    return@setNavigationItemSelectedListener true
                }
                else -> {
                    return@setNavigationItemSelectedListener menuItem.onNavDestinationSelected(navController)
                }
            }
        }
    }

    private fun navigateHome(isMaster: Boolean) {
        val homeDestinationId = if (isMaster) R.id.masterUserListFragment else R.id.mainFragment

        if (navController.currentDestination?.id == homeDestinationId) {
            Log.d(TAG, "Již v cílové destinaci ($homeDestinationId).")
            return
        }

        Log.d(TAG, "Naviguji na $homeDestinationId")
        try {
            val navOptions = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .build()
            navController.navigate(homeDestinationId, null, navOptions)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Chyba navigace na $homeDestinationId", e)
        }
    }

    private fun updateNavHeader(navView: NavigationView) {
        val headerView = navView.getHeaderView(0)
        val emailTextView = headerView.findViewById<TextView>(R.id.userEmailTextView)
        emailTextView.text = Firebase.auth.currentUser?.email ?: "Nepřihlášen"
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}