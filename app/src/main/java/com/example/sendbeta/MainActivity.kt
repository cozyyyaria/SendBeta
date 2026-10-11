package com.example.sendbeta

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
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
            loadFragment(fragment)
            true
        }
    }
    
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
