package com.swarnabook.billing

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.swarnabook.billing.databinding.ActivityMainBinding

/**
 * Single activity that hosts the navigation graph and the bottom navigation bar.
 * The bottom bar is hidden on the splash and full-screen invoice-view destinations.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_SwarnaBook)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get the NavController from the NavHostFragment itself. Calling
        // findNavController(viewId) directly in onCreate() crashes with a
        // FragmentContainerView, because the controller isn't attached to the
        // view yet at this point.
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav: BottomNavigationView = binding.bottomNav
        NavigationUI.setupWithNavController(bottomNav, navController)

        // Hide the bottom bar on screens that should be full-screen.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val hideBar = destination.id == R.id.splashFragment ||
                destination.id == R.id.invoiceViewFragment
            bottomNav.visibility = if (hideBar) View.GONE else View.VISIBLE
        }
    }
}
