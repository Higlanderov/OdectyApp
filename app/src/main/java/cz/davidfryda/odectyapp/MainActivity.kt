package cz.davidfryda.odectyapp

// --- ✨ PŘIDÁNO/UPRAVENO: Potřebné importy ---
import android.net.Uri // Potřebné pro Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest // Potřebné pro výběr obrázku
import androidx.activity.result.contract.ActivityResultContracts // Potřebné pro výběr obrázku
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit // Potřebné pro SharedPreferences editaci
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope // Potřebné pro Coroutines
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.ui.setupActionBarWithNavController
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder // Potřebné pro dialog
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import cz.davidfryda.odectyapp.data.UserData
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cz.davidfryda.odectyapp.workers.NotificationWorker
import kotlinx.coroutines.Dispatchers // Potřebné pro Coroutines
import kotlinx.coroutines.launch // Potřebné pro Coroutines
import kotlinx.coroutines.withContext // Potřebné pro Coroutines
import java.io.File // Potřebné pro práci se soubory
import java.io.FileOutputStream // Potřebné pro práci se soubory
import java.io.InputStream // Potřebné pro práci se soubory
import java.io.IOException // Potřebné pro práci se soubory
import java.util.concurrent.TimeUnit
// --- ✨ KONEC PŘIDANÝCH IMPORTŮ ---


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

    // --- ✨ PŘIDÁNO: ActivityResultLauncher pro výběr obrázku ---
    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Log.d(tag, "Vybráno URI obrázku: $uri")
            val user = Firebase.auth.currentUser
            if (user != null) {
                // Kopírování obrázku v Coroutine mimo hlavní vlákno
                lifecycleScope.launch(Dispatchers.IO) {
                    val internalPath = copyImageToInternalStorage(user.uid, uri)
                    if (internalPath != null) {
                        saveProfileImagePath(user.uid, internalPath)
                        // Aktualizaci UI musíme provést zpět na hlavním vlákně
                        withContext(Dispatchers.Main) {
                            updateNavHeader(findViewById(R.id.nav_view))
                        }
                    } else {
                        // Ošetření chyby kopírování (na hlavním vlákně)
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
    // --- ✨ KONEC PŘIDANÉHO LAUNCHERU ---


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        scheduleNotificationWorker()
    }


    // --- ✨ PŘIDÁNO: Funkce pro kopírování obrázku ---
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
            destinationFile.delete() // Smažeme nekompletní soubor
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
    // --- ✨ KONEC FUNKCE KOPÍROVÁNÍ ---

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

        // Vždy po změně viditelnosti ikony musíme aktualizovat i tečku
        updateBadgeVisibility()
    }

    private fun updateBadgeVisibility() {
        val shouldShowBadge = (notificationMenuItem?.isVisible == true) && hasUnreadNotifications
        Log.d(tag, "updateBadgeVisibility: shouldShowBadge = $shouldShowBadge (iconVisible=${notificationMenuItem?.isVisible}, hasUnread=$hasUnreadNotifications)")

        toolbar.post {
            try {
                notificationBadge?.let { badge ->
                    BadgeUtils.detachBadgeDrawable(badge, toolbar, R.id.action_notifications)

                    if (shouldShowBadge) {
                        badge.number = notificationBadge?.number ?: 0
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
        Log.d(tag, "onDestroy: Listener notifikací odpojen.")
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
            Log.d(tag, "Listener notifikací již běží, přepisuji.");
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


    // --- ✨ ZAČÁTEK UPRAVENÉ FUNKCE `updateNavHeader` ---
    private fun updateNavHeader(navView: NavigationView) {
        val headerView = navView.getHeaderView(0)
        val userNameTextView = headerView.findViewById<TextView>(R.id.userNameTextView)
        val userEmailTextView = headerView.findViewById<TextView>(R.id.userEmailTextView)
        val profileImageView = headerView.findViewById<ImageView>(R.id.profileImageView)

        val user = Firebase.auth.currentUser

        if (user != null) {
            val userId = user.uid // Uložíme si userId
            userEmailTextView.text = user.email ?: "N/A"

            // Načtení jména z Firestore (zůstává stejné)
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

            // Načtení obrázku (lokální > Google > default)
            val localImagePath = getProfileImagePath(userId)
            var isLocalImageLoaded = false // Příznak, zda jsme načetli lokální obrázek
            if (localImagePath != null) {
                val imageFile = File(localImagePath)
                if (imageFile.exists()) {
                    Log.d(tag, "Načítám lokální profilový obrázek pro $userId z: $localImagePath")
                    isLocalImageLoaded = true // Označíme, že lokální obrázek existuje
                    profileImageView.load(imageFile) {
                        crossfade(true)
                        placeholder(R.drawable.ic_profile)
                        error(R.drawable.ic_profile) // Zobrazíme default, pokud lokální selže
                        transformations(CircleCropTransformation())
                    }
                } else {
                    // Soubor neexistuje, i když cesta je v SharedPreferences
                    Log.w(tag, "Lokální soubor obrázku nenalezen: $localImagePath. Mažu preferenci.")
                    clearProfileImagePath(userId) // Odstraníme neplatnou cestu
                    loadGoogleOrDefaultImage(profileImageView, user) // Načteme Google/default
                }
            } else {
                // Lokální cesta neexistuje, načteme Google/default
                loadGoogleOrDefaultImage(profileImageView, user)
            }

            // Nastavení OnClickListeneru podle toho, zda je lokální obrázek načten
            if (isLocalImageLoaded) {
                profileImageView.setOnClickListener {
                    Log.d(tag, "Kliknuto na lokální profilový obrázek, zobrazuji dialog.")
                    showImageOptionsDialog(userId) // Voláme nový dialog
                }
            } else {
                profileImageView.setOnClickListener {
                    Log.d(tag, "Kliknuto na profilový obrázek (Google/default), spouštím výběr média.")
                    pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }

        } else {
            // Uživatel není přihlášen
            userNameTextView.text = getString(R.string.not_logged_in)
            userEmailTextView.text = ""
            profileImageView.setImageResource(R.drawable.ic_profile)
            profileImageView.setOnClickListener(null) // Odebrání listeneru
            unreadListener?.remove(); unreadListener = null; hasUnreadNotifications = false; notificationBadge?.number = 0; updateBadgeVisibility()
        }
    }
    // --- ✨ KONEC UPRAVENÉ FUNKCE `updateNavHeader` ---


    // --- ✨ PŘIDÁNO: Pomocná funkce pro Google/default obrázek ---
    private fun loadGoogleOrDefaultImage(imageView: ImageView, user: com.google.firebase.auth.FirebaseUser) {
        if (user.photoUrl != null) {
            Log.d(tag, "Načítám profilový obrázek z Google URL pro ${user.uid}")
            imageView.load(user.photoUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_profile)
                error(R.drawable.ic_profile) // Pokud Google URL selže, zobrazíme default
                transformations(CircleCropTransformation())
            }
        } else {
            Log.d(tag, "Google URL nenalezeno, nastavuji výchozí ikonu.")
            imageView.setImageResource(R.drawable.ic_profile) // Nastavíme defaultní drawable
        }
    }
    // --- ✨ KONEC POMOCNÉ FUNKCE ---


    // --- ✨ PŘIDÁNO: Funkce pro práci s SharedPreferences ---
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
    // --- ✨ KONEC FUNKCÍ PRO SHARED PREFERENCES ---


    // --- ✨ PŘIDÁNO: Funkce pro zobrazení dialogu možností ---
    private fun showImageOptionsDialog(userId: String) {
        val options = arrayOf(
            getString(R.string.change_picture),
            getString(R.string.remove_picture)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.change_profile_picture_title)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> { // Změnit
                        Log.d(tag, "Volba: Změnit obrázek.")
                        pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    1 -> { // Odstranit
                        Log.d(tag, "Volba: Odstranit obrázek.")
                        deleteLocalProfileImage(userId) // Zavoláme funkci pro smazání
                    }
                }
                // Dialog se zavře sám po výběru
            }
            .setNegativeButton(R.string.cancel, null) // Jen zavře dialog
            .show()
    }
    // --- ✨ KONEC FUNKCE PRO DIALOG ---


    // --- ✨ PŘIDÁNO: Funkce pro smazání lokálního obrázku ---
    private fun deleteLocalProfileImage(userId: String) {
        val localImagePath = getProfileImagePath(userId)
        if (localImagePath != null) {
            // Smazání souboru provádíme mimo hlavní vlákno
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
                    // Po smazání (nebo pokusu) odstraníme cestu z SharedPreferences
                    clearProfileImagePath(userId)
                    // Aktualizujeme hlavičku na hlavním vlákně
                    withContext(Dispatchers.Main) {
                        updateNavHeader(findViewById(R.id.nav_view))
                    }
                }
            }
        } else {
            Log.d(tag, "Pokus o smazání, ale lokální obrázek neexistuje v SharedPreferences.")
            // Pro jistotu aktualizujeme hlavičku, kdyby tam byl zobrazen omylem
            updateNavHeader(findViewById(R.id.nav_view))
        }
    }
    // --- ✨ KONEC FUNKCE PRO SMAZÁNÍ ---


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