package cz.davidfryda.odectyapp

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SplashFragment : Fragment(R.layout.fragment_splash) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            delay(1500)

            val currentUser = Firebase.auth.currentUser
            if (currentUser != null) {
                try {
                    // Díky perzistenci (v OdectyApplication) se tento dotaz pokusí
                    // načíst data z cache, pokud jsme offline.
                    val userDoc = Firebase.firestore.collection("users").document(currentUser.uid).get().await()
                    val isMaster = userDoc.getString("role") == "master"

                    if (isMaster) {
                        findNavController().navigate(R.id.action_splashFragment_to_masterUserListFragment)
                    } else {
                        findNavController().navigate(R.id.action_splashFragment_to_locationListFragment)
                    }
                } catch (_: Exception) {
                    // === ZAČÁTEK ÚPRAVY ===
                    // Původní kód zde navigoval na loginFragment.
                    // Nově: Pokud jsme se dostali sem, znamená to, že currentUser != null,
                    // ale selhalo načtení role (např. jsme offline a role není v cache).
                    // Místo na login navigujeme do hlavní části aplikace.
                    findNavController().navigate(R.id.action_splashFragment_to_locationListFragment)
                    // === KONEC ÚPRAVY ===
                }
            } else {
                findNavController().navigate(R.id.action_splashFragment_to_loginFragment)
            }
        }
    }
}