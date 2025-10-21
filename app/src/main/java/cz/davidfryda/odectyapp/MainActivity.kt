package cz.davidfryda.odectyapp

import android.os.Bundle
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
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    private var isUserMaster: Boolean? = null // Cache pro roli uživatele

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)

        // OPRAVA PROBLÉMU 1: Aplikujeme odsazení a správně vracíme insets
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val mainContentContainer: LinearLayout = view.findViewById(R.id.main_content_container)
            mainContentContainer.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)

            // Vrátíme původní insets, aby je mohly zpracovat i další komponenty
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

        // OPRAVA PROBLÉMU 2: Nastavíme vlastní chytrou logiku pro navigaci
        setupDrawerNavigation(navView, drawerLayout)

        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerOpened(drawerView: View) {
                updateNavHeader(navView)
            }
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        updateNavHeader(navView)
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
                    // Zkontrolujeme roli a přesměrujeme na správnou "domovskou" obrazovku
                    checkRoleAndNavigateHome()
                    return@setNavigationItemSelectedListener true
                }
                else -> {
                    // Pro všechny ostatní položky (Profil) použijeme standardní navigaci
                    return@setNavigationItemSelectedListener menuItem.onNavDestinationSelected(navController)
                }
            }
        }
    }

    private fun checkRoleAndNavigateHome() {
        // Pokud jsme roli už jednou zjišťovali, použijeme uloženou hodnotu
        if (isUserMaster != null) {
            navigateHome(isUserMaster!!)
            return
        }

        // Jinak se zeptáme databáze
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            Firebase.firestore.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    val isMaster = document.getString("role") == "master"
                    isUserMaster = isMaster // Uložíme si výsledek pro příště
                    navigateHome(isMaster)
                }
                .addOnFailureListener {
                    // V případě chyby se vrátíme na standardní obrazovku
                    navigateHome(false)
                }
        }
    }

    private fun navigateHome(isMaster: Boolean) {
        if (isMaster) {
            // Pokud jsme už na master obrazovce, nic neděláme
            if (navController.currentDestination?.id != R.id.masterUserListFragment) {
                navController.navigate(R.id.masterUserListFragment)
            }
        } else {
            // Pokud jsme už na user obrazovce, nic neděláme
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
