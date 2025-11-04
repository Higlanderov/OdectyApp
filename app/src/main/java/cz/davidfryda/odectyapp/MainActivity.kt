package cz.davidfryda.odectyapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import cz.davidfryda.odectyapp.data.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit


@ExperimentalBadgeUtils
class MainActivity : AppCompatActivity() {

    internal lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    private lateinit var auth: FirebaseAuth

    private var notificationBadge: BadgeDrawable? = null
    private var unreadListener: ListenerRegistration? = null
    private val db = Firebase.firestore

    private var notificationMenuItem: MenuItem? = null
    private var hasUnreadNotifications = false

    private lateinit var toolbar: Toolbar

    private val tag = "MainActivity"

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user == null) {
            val currentDestination = navController.currentDestination?.id
            val authScreens = setOf(
                R.id.loginFragment,
                R.id.registerFragment,
                R.id.splashFragment
            )

            if (currentDestination != null && currentDestination !in authScreens) {
                Log.d(tag, "User logged out, navigating to login")
                handleUserLogout(showBlockedMessage = false)
            }
        } else {
            user.getIdToken(true).addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e(tag, "Token refresh failed - possibly blocked: ${task.exception?.message}")
                    auth.signOut()
                    handleUserLogout(showBlockedMessage = true)
                }
            }
        }
    }

    private fun handleUserLogout(showBlockedMessage: Boolean) {
        runOnUiThread {
            if (navController.currentDestination?.id != R.id.loginFragment) {
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(navController.graph.findStartDestination().id, true, saveState = false)
                    .build()
                try {
                    navController.navigate(R.id.loginFragment, null, navOptions)
                    if (showBlockedMessage) {
                        Toast.makeText(this, "Váš účet byl zablokován", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Chyba při navigaci na login", e)
                }
            }
        }
    }

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Log.d(tag, "Vybráno URI obrázku: $uri")
            val user = Firebase.auth.currentUser
            if (user != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val internalPath = copyImageToInternalStorage(user.uid, uri)
                    if (internalPath != null) {
                        saveProfileImagePath(user.uid, internalPath)
                        withContext(Dispatchers.Main) {
                            updateNavHeader(findViewById(R.id.nav_view))
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Chyba při ukládání obrázku.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } else {
            Log.d(tag, "Výběr obrázku zrušen.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = Firebase.auth

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val mainContentContainer: LinearLayout? = view.findViewById(R.id.main_content_container)
            mainContentContainer?.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
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
                R.id.mainFragment, R.id.masterUserListFragment, R.id.locationListFragment
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        setupNotificationBadge()

        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerOpened(drawerView: View) {
                updateNavHeader(navView)
                // ✨ Aktualizuj master settings při otevření drawer
                setupMasterSettings(navView)
            }
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        setupDrawerNavigation(navView, drawerLayout)

        // ✨ Inicializuj master settings
        setupMasterSettings(navView)

        updateNavHeader(navView)
        updateFcmToken()

        createNotificationChannels()

        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d(tag, "Navigace na destinaci: ${destination.label} (ID: ${destination.id})")
            updateToolbarMenuVisibility(destination)

            val fragmentsWithCustomTitle = setOf(
                R.id.masterUserDetailFragment
            )

            if (destination.id in fragmentsWithCustomTitle) {
                Log.d(tag, "Fragment ${destination.id} si nastaví title sám - MainActivity ho nepřepisuje")
            } else {
                destination.label?.toString()?.let {
                    supportActionBar?.title = it
                    Log.d(tag, "MainActivity nastavilo title: $it")
                }
            }

            val currentDestinations = setOf(R.id.mainFragment, R.id.masterUserListFragment)
            if (destination.id in currentDestinations) {
                updateNavHeader(navView)
                Log.d(tag, "Header aktualizován po navigaci na domovskou obrazovku")
            }
        }

        scheduleNotificationWorker()

        auth.addAuthStateListener(authStateListener)
    }

    private fun createNotificationChannels() {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val readingsChannel = NotificationChannel(
                "new_readings",
                "Nové odečty",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Upozornění na nové odečty uživatelů"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
            }

            val monthlyRemindersChannel = NotificationChannel(
                "monthly_reminders",
                "Měsíční připomínky",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Připomínky k odeslání měsíčních odečtů"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
            }

            val localRemindersChannel = NotificationChannel(
                "reading_reminder_channel",
                "Lokální připomínky odečtů",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Záložní kanál pro lokální připomínky k provedení odečtu"
            }

            notificationManager.createNotificationChannel(readingsChannel)
            notificationManager.createNotificationChannel(monthlyRemindersChannel)
            notificationManager.createNotificationChannel(localRemindersChannel)

            Log.d(tag, "✅ Všechny notifikační kanály vytvořeny (včetně monthly_reminders)")
        }


    // ✨ UPRAVENO: Nastavení master settings - skrývá celou sekci pro běžné uživatele
    private fun setupMasterSettings(navView: NavigationView) {
        val user = Firebase.auth.currentUser
        val menu = navView.menu
        val masterGroup = menu.findItem(R.id.master_settings_section)
        val hideProfileItem = menu.findItem(R.id.hide_profile_action)

        if (user == null) {
            masterGroup?.isVisible = false
            // ✨ NOVÉ: Skryj také actionView (switch)
            hideProfileItem?.actionView?.visibility = View.GONE
            Log.d(tag, "Uživatel není přihlášen, master settings skryty")
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                val isMaster = document.getString("role") == "master"

                masterGroup?.isVisible = isMaster

                if (isMaster) {
                    Log.d(tag, "Master detekován, zobrazuji master settings")

                    // ✨ NOVÉ: Zobraz actionView (switch)
                    hideProfileItem?.actionView?.visibility = View.VISIBLE

                    navView.post {
                        val switchView = hideProfileItem?.actionView?.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchHideProfile)

                        if (switchView != null) {
                            val hideFromList = document.getBoolean("hideFromMasterList") ?: false

                            Log.d(tag, "Switch nalezen, nastavuji hodnotu: $hideFromList")

                            // Odstraň všechny listenery
                            switchView.setOnCheckedChangeListener(null)

                            // Nastav stav
                            switchView.isChecked = hideFromList

                            // Ujisti se, že je switch aktivní
                            switchView.isClickable = true
                            switchView.isEnabled = true

                            // Přidej listener
                            switchView.setOnCheckedChangeListener { buttonView, isChecked ->
                                Log.d(tag, "Switch changed to: $isChecked")
                                updateHideFromMasterList(user.uid, isChecked)
                            }

                            Log.d(tag, "✅ Master switch nastaven: hideFromList=$hideFromList, clickable=${switchView.isClickable}, enabled=${switchView.isEnabled}")
                        } else {
                            Log.e(tag, "❌ Switch nebyl nalezen v actionView")
                        }
                    }
                } else {
                    Log.d(tag, "Běžný uživatel, master settings skryty")

                    // ✨ DŮLEŽITÉ: Explicitně skryj actionView (switch) pro běžné uživatele
                    hideProfileItem?.actionView?.visibility = View.GONE
                    Log.d(tag, "ActionView (switch) nastaven na GONE")
                }
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Chyba při kontrole role uživatele", e)
                masterGroup?.isVisible = false
                // ✨ NOVÉ: Skryj také actionView při chybě
                hideProfileItem?.actionView?.visibility = View.GONE
            }
    }

    // ✨ METODA ZŮSTÁVÁ STEJNÁ: Uložit nastavení hideFromMasterList do Firestore
    private fun updateHideFromMasterList(userId: String, hide: Boolean) {
        db.collection("users").document(userId)
            .update("hideFromMasterList", hide)
            .addOnSuccessListener {
                val message = if (hide) {
                    "Váš profil je nyní skrytý"
                } else {
                    "Váš profil je nyní viditelný"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                Log.d(tag, "✅ hideFromMasterList aktualizováno na: $hide")
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Chyba při aktualizaci nastavení", Toast.LENGTH_SHORT).show()
                Log.e(tag, "❌ Chyba při ukládání hideFromMasterList", e)
            }
    }

    private fun copyImageToInternalStorage(userId: String, sourceUri: Uri): String? {
        val fileName = "profile_image_$userId.jpg"
        val destinationFile = File(filesDir, fileName)

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            inputStream = contentResolver.openInputStream(sourceUri)
            outputStream = FileOutputStream(destinationFile)
            if (inputStream != null) {
                inputStream.copyTo(outputStream)
                Log.d(tag, "Obrázek zkopírován do: ${destinationFile.absolutePath}")
                return destinationFile.absolutePath
            } else {
                Log.e(tag, "Nepodařilo se otevřít InputStream pro URI: $sourceUri")
                return null
            }
        } catch (e: Exception) {
            Log.e(tag, "Chyba při kopírování obrázku do interního úložiště.", e)
            destinationFile.delete()
            return null
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (ioe: IOException) {
                Log.e(tag, "Chyba při zavírání streamů.", ioe)
            }
        }
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
        toolbar.post {
            try {
                notificationBadge?.let { badge ->
                    val shouldShowBadge = (notificationMenuItem?.isVisible == true) && hasUnreadNotifications
                    Log.d(tag, "updateBadgeVisibility (v post): shouldShowBadge = $shouldShowBadge (iconVisible=${notificationMenuItem?.isVisible}, hasUnread=$hasUnreadNotifications)")

                    BadgeUtils.detachBadgeDrawable(badge, toolbar, R.id.action_notifications)

                    if (shouldShowBadge) {
                        BadgeUtils.attachBadgeDrawable(badge, toolbar, R.id.action_notifications)
                        Log.d(tag, "Badge připojen s počtem: ${badge.number}")
                    } else {
                        Log.d(tag, "Badge zůstává odpojen.")
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Chyba při (od)připojování badge: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unreadListener?.remove()
        auth.removeAuthStateListener(authStateListener)
        Log.d(tag, "onDestroy: Listener notifikací a auth odpojen.")
    }

    private fun setupNotificationBadge() {
        Log.d(tag, "setupNotificationBadge voláno.")
        notificationBadge = BadgeDrawable.create(this).apply {
            isVisible = true
            number = 0
        }
    }

    private fun listenForUnreadNotifications(masterUserId: String) {
        Log.d(tag, "Spouštím listener pro notifikace mastera ID: $masterUserId")
        if (unreadListener != null) {
            Log.d(tag, "Listener notifikací již běží, přepisuji.")
            unreadListener?.remove()
        }

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
        Log.d(tag, "onCreateOptionsMenu: notificationMenuItem nastaven.")

        navController.currentDestination?.let { updateToolbarMenuVisibility(it) }

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
                R.id.logout_action -> {
                    // ✨ NOVÉ: Smaž FCM token při odhlášení
                    val currentUser = Firebase.auth.currentUser
                    if (currentUser != null) {
                        Log.d(tag, "Mazání FCM tokenu pro uživatele: ${currentUser.uid}")
                        db.collection("users").document(currentUser.uid)
                            .update("fcmToken", com.google.firebase.firestore.FieldValue.delete())
                            .addOnSuccessListener {
                                Log.d(tag, "✅ FCM token úspěšně smazán z Firestore")
                            }
                            .addOnFailureListener { e ->
                                Log.e(tag, "❌ Chyba při mazání FCM tokenu", e)
                            }
                    }

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
                R.id.hide_profile_action -> {
                    // Kliknutí na položku "Skrýt profil" - nic nedělat, Switch se ovládá sám
                    return@setNavigationItemSelectedListener false
                }
                else -> {
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
            val userId = user.uid
            userEmailTextView.text = user.email ?: "N/A"

            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    val userData = document.toObject(UserData::class.java)
                    userNameTextView.text = if (userData != null && userData.name.isNotEmpty()) {
                        "${userData.name} ${userData.surname}"
                    } else {
                        user.displayName ?: getString(R.string.default_username)
                    }
                    if (document.getString("role") == "master") {
                        listenForUnreadNotifications(userId)
                    } else {
                        unreadListener?.remove(); unreadListener = null; hasUnreadNotifications = false; notificationBadge?.number = 0; updateBadgeVisibility()
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(tag, "Chyba při načítání UserData pro hlavičku", e)
                    userNameTextView.text = user.displayName ?: getString(R.string.default_username)
                    unreadListener?.remove(); unreadListener = null; hasUnreadNotifications = false; notificationBadge?.number = 0; updateBadgeVisibility()
                }

            val localImagePath = getProfileImagePath(userId)
            var isLocalImageLoaded = false
            if (localImagePath != null) {
                val imageFile = File(localImagePath)
                if (imageFile.exists()) {
                    Log.d(tag, "Načítám lokální profilový obrázek pro $userId z: $localImagePath")
                    isLocalImageLoaded = true
                    profileImageView.load(imageFile) {
                        crossfade(true)
                        placeholder(R.drawable.ic_profile)
                        error(R.drawable.ic_profile)
                        transformations(CircleCropTransformation())
                    }
                } else {
                    Log.w(tag, "Lokální soubor obrázku nenalezen: $localImagePath. Mažu preferenci.")
                    clearProfileImagePath(userId)
                    loadGoogleOrDefaultImage(profileImageView, user)
                }
            } else {
                loadGoogleOrDefaultImage(profileImageView, user)
            }

            if (isLocalImageLoaded) {
                profileImageView.setOnClickListener {
                    Log.d(tag, "Kliknuto na lokální profilový obrázek, zobrazuji dialog.")
                    showImageOptionsDialog(userId)
                }
            } else {
                profileImageView.setOnClickListener {
                    Log.d(tag, "Kliknuto na profilový obrázek (Google/default), spouštím výběr média.")
                    pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }

        } else {
            userNameTextView.text = getString(R.string.not_logged_in)
            userEmailTextView.text = ""
            profileImageView.setImageResource(R.drawable.ic_profile)
            profileImageView.setOnClickListener(null)
            unreadListener?.remove(); unreadListener = null; hasUnreadNotifications = false; notificationBadge?.number = 0; updateBadgeVisibility()
        }
    }

    private fun loadGoogleOrDefaultImage(imageView: ImageView, user: com.google.firebase.auth.FirebaseUser) {
        if (user.photoUrl != null) {
            Log.d(tag, "Načítám profilový obrázek z Google URL pro ${user.uid}")
            imageView.load(user.photoUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_profile)
                error(R.drawable.ic_profile)
                transformations(CircleCropTransformation())
            }
        } else {
            Log.d(tag, "Google URL nenalezeno, nastavuji výchozí ikonu.")
            imageView.setImageResource(R.drawable.ic_profile)
        }
    }

    private fun saveProfileImagePath(userId: String, path: String) {
        val sharedPref = getSharedPreferences("profile_images", MODE_PRIVATE) ?: return
        sharedPref.edit {
            putString("profile_image_path_$userId", path)
        }
        Log.d(tag, "Uložena cesta '$path' pro uživatele $userId")
    }

    private fun getProfileImagePath(userId: String): String? {
        val sharedPref = getSharedPreferences("profile_images", MODE_PRIVATE)
        return sharedPref.getString("profile_image_path_$userId", null)
    }

    private fun clearProfileImagePath(userId: String) {
        val sharedPref = getSharedPreferences("profile_images", MODE_PRIVATE) ?: return
        sharedPref.edit {
            remove("profile_image_path_$userId")
        }
        Log.d(tag, "Smazána cesta pro uživatele $userId")
    }

    private fun showImageOptionsDialog(userId: String) {
        val options = arrayOf(
            getString(R.string.change_picture),
            getString(R.string.remove_picture)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.change_profile_picture_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        Log.d(tag, "Volba: Změnit obrázek.")
                        pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    1 -> {
                        Log.d(tag, "Volba: Odstranit obrázek.")
                        deleteLocalProfileImage(userId)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteLocalProfileImage(userId: String) {
        val localImagePath = getProfileImagePath(userId)
        if (localImagePath != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val file = File(localImagePath)
                    if (file.exists()) {
                        if (file.delete()) {
                            Log.d(tag, "Lokální obrázek smazán: $localImagePath")
                        } else {
                            Log.w(tag, "Nepodařilo se smazat lokální obrázek: $localImagePath")
                        }
                    } else {
                        Log.w(tag, "Lokální obrázek pro smazání nenalezen: $localImagePath")
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Chyba při mazání lokálního obrázku: $localImagePath", e)
                } finally {
                    clearProfileImagePath(userId)
                    withContext(Dispatchers.Main) {
                        updateNavHeader(findViewById(R.id.nav_view))
                    }
                }
            }
        } else {
            Log.d(tag, "Pokus o smazání, ale lokální obrázek neexistuje v SharedPreferences.")
            updateNavHeader(findViewById(R.id.nav_view))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun scheduleNotificationWorker() {
        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "NotificationWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        Log.d(tag, "NotificationWorker naplánován.")
    }
}