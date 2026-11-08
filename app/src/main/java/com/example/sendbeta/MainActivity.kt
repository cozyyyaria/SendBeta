package com.example.sendbeta

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.sendbeta.ui.HomeFragment
import com.example.sendbeta.ui.RoutesFragment
import com.example.sendbeta.ui.CaptureFragment
import com.example.sendbeta.ui.MapFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    
    private lateinit var bottomNav: BottomNavigationView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Handle system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
        
        bottomNav = findViewById(R.id.bottom_navigation)
        
        // Set default fragment
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }
        
        // Handle navigation
        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_routes -> RoutesFragment()
                R.id.nav_capture -> CaptureFragment()
                R.id.nav_gyms -> MapFragment()
                else -> HomeFragment()
            }
            // Clear any detail screens from the previous tab so Back behaves predictably
            supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            loadFragment(fragment)
            true
        }

        // Sync bottom navigation selection with current fragment when popping back stack
        supportFragmentManager.addOnBackStackChangedListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            when (currentFragment) {
                is HomeFragment -> bottomNav.menu.findItem(R.id.nav_home)?.isChecked = true
                is RoutesFragment -> bottomNav.menu.findItem(R.id.nav_routes)?.isChecked = true
                is CaptureFragment -> bottomNav.menu.findItem(R.id.nav_capture)?.isChecked = true
                is MapFragment -> bottomNav.menu.findItem(R.id.nav_gyms)?.isChecked = true
            }
        }
    }
    
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
