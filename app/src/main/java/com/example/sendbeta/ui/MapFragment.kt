package com.example.sendbeta.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.sendbeta.R

class MapFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up directions button
        view.findViewById<Button>(R.id.btn_directions)?.setOnClickListener {
            val address = "388 Carlaw Ave, Toronto, ON"
            val mapUri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, mapUri)
            try {
                startActivity(mapIntent)
            } catch (e: Exception) {
                // Fallback to web browser maps URL
                try {
                    val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(address)}")
                    startActivity(Intent(Intent.ACTION_VIEW, webUri))
                } catch (ex: Exception) {
                    Toast.makeText(context, "Could not open directions", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Set up gym markers click handlers
        view.findViewById<View>(R.id.gym_marker_1)?.setOnClickListener {
            selectGym("The Rock Oasis", "388 Carlaw Ave, Toronto, ON", "2.4 km", "Open")
        }

        view.findViewById<View>(R.id.gym_marker_2)?.setOnClickListener {
            selectGym("Basecamp Climbing", "123 Queen St W, Toronto, ON", "3.1 km", "Open")
        }

        view.findViewById<View>(R.id.gym_marker_3)?.setOnClickListener {
            selectGym("Joe Rockhead's", "29 Fraser Ave, Toronto, ON", "4.2 km", "Open")
        }
    }

    private fun selectGym(name: String, address: String, distance: String, hours: String) {
        view?.let { v ->
            v.findViewById<TextView>(R.id.gym_name)?.text = name
            v.findViewById<TextView>(R.id.gym_address)?.text = address
            v.findViewById<TextView>(R.id.gym_distance)?.text = distance
            v.findViewById<TextView>(R.id.gym_hours)?.text = hours
        }
    }
}
