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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
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

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    private var isUserMaster: Boolean? = null

    // NOVÉ: Pro notifikační puntík
    private var notificationBadge: BadgeDrawable? = null
    private var unreadListener: ListenerRegistration? = null
    private val db = Firebase.firestore

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

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
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
        setupNotificationBadge() // Inicializujeme puntík

        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerOpened(drawerView: View) { updateNavHeader(navView) }
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        updateNavHeader(navView)
        updateFcmToken()
    }

    // NOVÁ METODA: Inicializuje BadgeDrawable
    private fun setupNotificationBadge() {
        notificationBadge = BadgeDrawable.create(this).apply {
            isVisible = false
        }
    }

    // NOVÁ METODA: Spustí naslouchání nepřečteným notifikacím
    private fun listenForUnreadNotifications(masterUserId: String) {
        unreadListener?.remove() // Odstraníme starý listener, pokud existuje
        unreadListener = db.collection("notifications").document(masterUserId).collection("items")
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                // Zobrazíme nebo skryjeme puntík podle počtu
                notificationBadge?.isVisible = snapshots != null && !snapshots.isEmpty
            }
    }

    // NOVÁ METODA: Přidá ikonu do horní lišty
    @ExperimentalBadgeUtils
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        // Připojíme náš puntík k ikoně
        notificationBadge?.let {
            BadgeUtils.attachBadgeDrawable(it, findViewById(R.id.toolbar), R.id.action_notifications)
        }
        return true
    }

    // NOVÁ METODA: Zpracuje kliknutí na ikonu
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_notifications -> {
                // Otevřeme dialog se seznamem notifikací
                navController.navigate(R.id.notificationListFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateFcmToken() {
        val currentUser = Firebase.auth.currentUser ?: return // Pokud nikdo není přihlášen, skončíme

        Firebase.messaging.token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("MainActivity", "Načtení FCM tokenu selhalo", task.exception)
                return@addOnCompleteListener
            }

            // Máme token, uložíme ho do databáze
            val token = task.result
            val userDocRef = Firebase.firestore.collection("users").document(currentUser.uid)

            // Použijeme .set s merge(), aby se token uložil, i pokud uživatel
            // ještě nemá vytvořený dokument (např. při registraci přes Google)
            userDocRef.set(mapOf("fcmToken" to token), SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("MainActivity", "FCM Token úspěšně uložen/aktualizován.")
                }
                .addOnFailureListener { e ->
                    Log.w("MainActivity", "Uložení FCM Tokenu selhalo", e)
                }
        }
    }

    private fun setupDrawerNavigation(navView: NavigationView, drawerLayout: DrawerLayout) {
        navView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.close()

            when (menuItem.itemId) {
                R.id.logout_action -> {
                    Firebase.auth.signOut()
                    isUserMaster = null // Resetujeme cache role
                    navController.navigate(R.id.action_global_splashFragment)
                    return@setNavigationItemSelectedListener true
                }
                R.id.mainFragment -> {
                    checkRoleAndNavigateHome()
                    return@setNavigationItemSelectedListener true
                }
                else -> {
                    return@setNavigationItemSelectedListener menuItem.onNavDestinationSelected(navController)
                }
            }
        }
    }

    private fun checkRoleAndNavigateHome() {
        if (isUserMaster != null) {
            navigateHome(isUserMaster!!)
            return
        }

        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            Firebase.firestore.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    val isMaster = document.getString("role") == "master"
                    isUserMaster = isMaster

                    // NOVÉ: Pokud je master, spustíme listener notifikací
                    if (isMaster) {
                        listenForUnreadNotifications(currentUser.uid)
                    } else {
                        unreadListener?.remove() // Pokud není master, odpojíme listener
                        notificationBadge?.isVisible = false
                    }

                    navigateHome(isMaster)
                }
                .addOnFailureListener {
                    navigateHome(false)
                }
        }
    }

    private fun navigateHome(isMaster: Boolean) {
        if (isMaster) {
            if (navController.currentDestination?.id != R.id.masterUserListFragment) {
                navController.navigate(R.id.masterUserListFragment)
            }
        } else {
            if (navController.currentDestination?.id != R.id.mainFragment) {
                navController.navigate(R.id.mainFragment)
            }
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
