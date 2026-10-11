package com.example.sendbeta.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.sendbeta.R

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Avatar button -> AboutFragment
        view.findViewById<TextView>(R.id.btn_avatar)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AboutFragment())
                .addToBackStack(null)
                .commit()
        }

        // Record Beta button -> CaptureFragment
        view.findViewById<Button>(R.id.btn_record_beta)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CaptureFragment())
                .addToBackStack(null)
                .commit()
        }

        // View All Routes -> RoutesFragment
        view.findViewById<TextView>(R.id.btn_view_all_routes)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RoutesFragment())
                .addToBackStack(null)
                .commit()
        }

        // Attempt cards -> AnalyzerFragment
        view.findViewById<View>(R.id.attempt_card_1)?.setOnClickListener { openAnalyzer() }
        view.findViewById<View>(R.id.attempt_card_2)?.setOnClickListener { openAnalyzer() }
        view.findViewById<View>(R.id.attempt_card_3)?.setOnClickListener { openAnalyzer() }
    }

    private fun openAnalyzer() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AnalyzerFragment())
            .addToBackStack(null)
            .commit()
    }
}
