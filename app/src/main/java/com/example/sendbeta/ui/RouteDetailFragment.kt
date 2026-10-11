package com.example.sendbeta.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.sendbeta.R

class RouteDetailFragment : Fragment() {

    private var routeName: String = "The Pink Arête"
    private var gymName: String = "Rock Oasis · Toronto"
    private var grade: String = "V5"

    companion object {
        fun newInstance(routeName: String, gymName: String, grade: String): RouteDetailFragment {
            val fragment = RouteDetailFragment()
            val args = Bundle().apply {
                putString("route_name", routeName)
                putString("gym_name", gymName)
                putString("grade", grade)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            routeName = it.getString("route_name", routeName)
            gymName = it.getString("gym_name", gymName)
            grade = it.getString("grade", grade)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_route_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Populate fields
        view.findViewById<TextView>(R.id.route_detail_title)?.text = routeName
        view.findViewById<TextView>(R.id.route_detail_gym)?.text = gymName
        view.findViewById<TextView>(R.id.banner_grade)?.text = grade
        view.findViewById<TextView>(R.id.profile_grade)?.text = grade

        // Back button
        view.findViewById<ImageButton>(R.id.btn_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Share button
        view.findViewById<ImageButton>(R.id.btn_share)?.setOnClickListener {
            Toast.makeText(context, "Sharing route $routeName...", Toast.LENGTH_SHORT).show()
        }

        // Attempt rows
        view.findViewById<View>(R.id.attempt_row_1)?.setOnClickListener {
            openAnalyzer()
        }

        view.findViewById<View>(R.id.attempt_row_2)?.setOnClickListener {
            openAnalyzer()
        }

        // View on map button
        view.findViewById<Button>(R.id.btn_view_map)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MapFragment())
                .addToBackStack(null)
                .commit()
        }

        // Log attempt button
        view.findViewById<Button>(R.id.btn_log_attempt)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CaptureFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun openAnalyzer() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AnalyzerFragment())
            .addToBackStack(null)
            .commit()
    }
}
