package cz.davidfryda.odectyapp

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast // <-- PŘIDÁN IMPORT
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
import androidx.navigation.ui.onNavDestinationSelected // Ověř, že tento import funguje
import androidx.navigation.ui.setupActionBarWithNavController
import coil.load
import coil.transform.CircleCropTransformation
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
import cz.davidfryda.odectyapp.data.UserData
// Importy pro WorkManager - ověř, zda jsou správné
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cz.davidfryda.odectyapp.workers.NotificationWorker // <-- Ověř cestu k Workeru
import java.util.concurrent.TimeUnit // <-- Import pro TimeUnit

@ExperimentalBadgeUtils // Potřebné pro BadgeUtils
class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController

    private var notificationBadge: BadgeDrawable? = null
    private var unreadListener: ListenerRegistration? = null
    private val db = Firebase.firestore

    private var notificationMenuItem: MenuItem? = null
    private var hasUnreadNotifications = false

    private lateinit var toolbar: Toolbar

    private val tag = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val mainContentContainer: LinearLayout? = view.findViewById(R.id.main_content_container) // Změněno na nullable pro jistotu
            // --- START ZMĚNY: Odstraněn nepotřebný safe call ---
            mainContentContainer?.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            // --- KONEC ZMĚNY ---
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
                R.id.mainFragment, R.id.masterUserListFragment
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        setupNotificationBadge()

        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerOpened(drawerView: View) { updateNavHeader(navView) }
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        setupDrawerNavigation(navView, drawerLayout)

        updateNavHeader(navView)
        updateFcmToken()

        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d(tag, "Navigace na destinaci: ${destination.label} (ID: ${destination.id})")
            updateToolbarMenuVisibility(destination)
        }

        // Plánování Workeru pro kontrolu notifikací - přesunuto sem z konce
        scheduleNotificationWorker()
    }

    private fun updateToolbarMenuVisibility(destination: NavDestination) {
        val hideBellOnDestinations = setOf(
            R.id.splashFragment,
            R.id.loginFragment,
            R.id.registerFragment,
            R.id.userInfoFragment
        )
        val shouldHideBell = destination.id in hideBellOnDestinations

        notificationMenuItem?.isVisible = !shouldHideBell
        Log.d(tag, "Viditelnost zvonečku nastavena na: ${!shouldHideBell}")
        updateBadgeVisibility()
    }

    private fun updateBadgeVisibility() {
        val shouldShowBadge = (notificationMenuItem?.isVisible == true) && hasUnreadNotifications
        Log.d(tag, "updateBadgeVisibility: shouldShowBadge = $shouldShowBadge (isVisible=${notificationMenuItem?.isVisible}, hasUnread=$hasUnreadNotifications)")

        notificationBadge?.isVisible = shouldShowBadge

        if (shouldShowBadge || notificationMenuItem?.isVisible == true) {
            toolbar.post {
                try {
                    notificationBadge?.let { badge ->
                        BadgeUtils.detachBadgeDrawable(badge, toolbar, R.id.action_notifications)
                        if (shouldShowBadge) {
                            BadgeUtils.attachBadgeDrawable(badge, toolbar, R.id.action_notifications)
                            Log.d(tag, "Badge znovu připojen")
                        } else {
                            Log.d(tag, "Badge zůstává odpojen")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Chyba při (od)připojování badge", e)
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        unreadListener?.remove()
        Log.d(tag, "onDestroy: Listener notifikací odpojen.")
    }

    private fun setupNotificationBadge() {
        Log.d(tag, "setupNotificationBadge voláno.")
        notificationBadge = BadgeDrawable.create(this).apply {
            isVisible = false
            number = 0
        }
    }

    private fun listenForUnreadNotifications(masterUserId: String) {
        Log.d(tag, "Spouštím listener pro notifikace mastera ID: $masterUserId")
        if (unreadListener != null) {
            Log.d(tag, "Listener notifikací již běží.")
            // return // Můžeme nechat běžet, Firestore by měl zvládnout duplicitní listenery
        }
        unreadListener?.remove()

        unreadListener = db.collection("notifications").document(masterUserId).collection("items")
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(tag, "Chyba při naslouchání notifikacím:", error)
                    hasUnreadNotifications = false
                    notificationBadge?.number = 0
                    updateBadgeVisibility()
                    return@addSnapshotListener
                }

                val count = snapshots?.size() ?: 0
                hasUnreadNotifications = count > 0
                notificationBadge?.number = count

                Log.d(tag, "Listener notifikací: Nepřečteno=$count, hasUnread=$hasUnreadNotifications")
                updateBadgeVisibility()
            }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        notificationMenuItem = menu.findItem(R.id.action_notifications)

        navController.currentDestination?.let { updateToolbarMenuVisibility(it) }

        toolbar.post {
            notificationBadge?.let { badge ->
                Log.d(tag, "Připojuji badge k toolbaru.")
                try {
                    BadgeUtils.detachBadgeDrawable(badge, toolbar, R.id.action_notifications)
                    BadgeUtils.attachBadgeDrawable(badge, toolbar, R.id.action_notifications)
                    updateBadgeVisibility()
                    Log.d(tag, "Badge úspěšně připojen")
                } catch (e: Exception) {
                    Log.e(tag, "Chyba při připojování badge", e)
                }
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_notifications -> {
                try {
                    navController.navigate(R.id.notificationListFragment)
                } catch (e: Exception) {
                    Log.e(tag, "Chyba navigace na NotificationListFragment", e)
                    Toast.makeText(this, "Nelze zobrazit notifikace.", Toast.LENGTH_SHORT).show()
                }
                true
            }
            // Zpracování kliknutí na home/up tlačítko (šipka zpět nebo hamburger ikona)
            // se nyní děje v onSupportNavigateUp(), není potřeba zde řešit android.R.id.home
            else -> item.onNavDestinationSelected(navController) || super.onOptionsItemSelected(item)
        }
    }

    private fun updateFcmToken() {
        val currentUser = Firebase.auth.currentUser ?: return
        Firebase.messaging.token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(tag, "Načtení FCM tokenu selhalo", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            val userDocRef = Firebase.firestore.collection("users").document(currentUser.uid)
            userDocRef.set(mapOf("fcmToken" to token), SetOptions.merge())
                .addOnSuccessListener { Log.d(tag, "FCM Token uložen/aktualizován.") }
                .addOnFailureListener { e -> Log.w(tag, "Uložení FCM Tokenu selhalo", e) }
        }
    }

    private fun setupDrawerNavigation(navView: NavigationView, drawerLayout: DrawerLayout) {
        navView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawers()

            when (menuItem.itemId) {
                // --- START ZMĚNY: Použito správné ID pro logout ---
                R.id.logout_action -> {
                    // --- KONEC ZMĚNY ---
                    Firebase.auth.signOut()
                    hasUnreadNotifications = false
                    unreadListener?.remove()
                    unreadListener = null
                    notificationBadge?.number = 0
                    updateBadgeVisibility()
                    Log.d(tag, "Odhlášení uživatele.")
                    val navOptions = NavOptions.Builder()
                        .setPopUpTo(navController.graph.findStartDestination().id, true, saveState = false)
                        .build()
                    try {
                        navController.navigate(R.id.splashFragment, null, navOptions)
                    } catch (e: Exception) {
                        Log.e(tag, "Chyba navigace na SplashFragment po odhlášení", e)
                        try {
                            navController.navigate(R.id.loginFragment, null, navOptions)
                        } catch (e2: Exception) {
                            Log.e(tag, "Chyba navigace i na LoginFragment po odhlášení", e2)
                        }
                    }
                    return@setNavigationItemSelectedListener true
                }
                R.id.mainFragment, R.id.masterUserListFragment -> {
                    navigateHome()
                    return@setNavigationItemSelectedListener true
                }
                // --- START ZMĚNY: ID pro notifikace z drawer menu ---
                // Ověř, že R.id.notifications existuje v drawer_menu.xml, pokud tam má být
                // Pokud tam není, tuto část odstraň. Pokud ano, odkomentuj:
                /*
                R.id.notifications -> {
                    try {
                        navController.navigate(R.id.notificationListFragment)
                    } catch (e: Exception) {
                        Log.e(tag, "Chyba navigace na NotificationListFragment z draweru", e)
                        Toast.makeText(this, "Nelze zobrazit notifikace.", Toast.LENGTH_SHORT).show()
                    }
                    return@setNavigationItemSelectedListener true
                }
                 */
                // --- KONEC ZMĚNY ---
                else -> {
                    // Výchozí chování - necháme NavController navigovat
                    return@setNavigationItemSelectedListener menuItem.onNavDestinationSelected(navController)
                }
            }
        }
    }

    private fun navigateHome() {
        val user = Firebase.auth.currentUser
        if (user == null) {
            val navOptions = NavOptions.Builder()
                .setPopUpTo(navController.graph.findStartDestination().id, true, saveState = false)
                .build()
            navController.navigate(R.id.splashFragment, null, navOptions)
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                val isMaster = document.getString("role") == "master"
                val homeDestinationId = if (isMaster) R.id.masterUserListFragment else R.id.mainFragment

                if (navController.currentDestination?.id != homeDestinationId) {
                    val navOptions = NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setPopUpTo(navController.graph.findStartDestination().id, false, saveState = true)
                        .setRestoreState(true)
                        .build()
                    try {
                        navController.navigate(homeDestinationId, null, navOptions)
                    } catch (e: Exception) {
                        Log.e(tag, "Chyba navigace domů na $homeDestinationId", e)
                    }
                } else {
                    Log.d(tag,"Už jsme na domovské obrazovce ($homeDestinationId).")
                }
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Chyba při zjišťování role pro navigaci domů", e)
                if (navController.currentDestination?.id != R.id.mainFragment) {
                    val navOptions = NavOptions.Builder().setLaunchSingleTop(true).setPopUpTo(navController.graph.findStartDestination().id, false, saveState = true).setRestoreState(true).build()
                    navController.navigate(R.id.mainFragment, null, navOptions)
                }
            }
    }

    private fun updateNavHeader(navView: NavigationView) {
        val headerView = navView.getHeaderView(0)
        val userNameTextView = headerView.findViewById<TextView>(R.id.userNameTextView)
        val userEmailTextView = headerView.findViewById<TextView>(R.id.userEmailTextView)
        val profileImageView = headerView.findViewById<ImageView>(R.id.profileImageView)

        val user = Firebase.auth.currentUser

        if (user != null) {
            userEmailTextView.text = user.email ?: "N/A"

            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    val userData = document.toObject(UserData::class.java)
                    userNameTextView.text = if (userData != null && userData.name.isNotEmpty()) {
                        "${userData.name} ${userData.surname}"
                    } else {
                        // --- START ZMĚNY: Použití string resource ---
                        user.displayName ?: getString(R.string.default_username)
                        // --- KONEC ZMĚNY ---
                    }
                    if (document.getString("role") == "master") {
                        listenForUnreadNotifications(user.uid)
                    } else {
                        unreadListener?.remove()
                        unreadListener = null
                        hasUnreadNotifications = false
                        notificationBadge?.number = 0
                        updateBadgeVisibility()
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(tag, "Chyba při načítání UserData pro hlavičku", e)
                    // --- START ZMĚNY: Použití string resource ---
                    userNameTextView.text = user.displayName ?: getString(R.string.default_username)
                    // --- KONEC ZMĚNY ---
                    unreadListener?.remove()
                    unreadListener = null
                    hasUnreadNotifications = false
                    notificationBadge?.number = 0
                    updateBadgeVisibility()
                }

            user.photoUrl?.let { photoUrl ->
                profileImageView.load(photoUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_profile)
                    error(R.drawable.ic_profile)
                    transformations(CircleCropTransformation())
                }
            } ?: run {
                profileImageView.setImageResource(R.drawable.ic_profile)
            }

        } else {
            // --- START ZMĚNY: Použití string resource ---
            userNameTextView.text = getString(R.string.not_logged_in)
            // --- KONEC ZMĚNY ---
            userEmailTextView.text = ""
            profileImageView.setImageResource(R.drawable.ic_profile)
            unreadListener?.remove()
            unreadListener = null
            hasUnreadNotifications = false
            notificationBadge?.number = 0
            updateBadgeVisibility()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    // Plánování Workeru
    private fun scheduleNotificationWorker() {
        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.DAYS)
            .build()

        // --- START ZMĚNY: Použití správného kontextu ---
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "NotificationWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        // --- KONEC ZMĚNY ---
        Log.d(tag, "NotificationWorker naplánován.")
    }
}