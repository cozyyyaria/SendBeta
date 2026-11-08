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

    private var currentGymAddress: String = "388 Carlaw Ave, Toronto, ON"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Directions button
        view.findViewById<Button>(R.id.btn_directions)?.setOnClickListener {
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(currentGymAddress)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
            try {
                startActivity(mapIntent)
            } catch (_: Exception) {
                try {
                    val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(currentGymAddress)}")
                    startActivity(Intent(Intent.ACTION_VIEW, webUri))
                } catch (ex: Exception) {
                    Toast.makeText(context, "Could not open directions", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Gym marker click handlers
        view.findViewById<View>(R.id.gym_marker_1)?.setOnClickListener {
            selectGym("The Rock Oasis", "388 Carlaw Ave, Toronto, ON", "2.4 km", "Open until 11 PM")
        }
        
        view.findViewById<View>(R.id.gym_marker_2)?.setOnClickListener {
            selectGym("Basecamp", "677 Bloor St W, Toronto, ON", "4.8 km", "Open until 11 PM")
        }
        
        view.findViewById<View>(R.id.gym_marker_3)?.setOnClickListener {
            selectGym("Joe Rockhead's", "29 Fraser Ave, Toronto, ON", "6.1 km", "Open until 11 PM")
        }
    }
    
    private fun selectGym(name: String, address: String, distance: String, hours: String) {
        currentGymAddress = address
        view?.let { v ->
            v.findViewById<TextView>(R.id.gym_name)?.text = name
            v.findViewById<TextView>(R.id.gym_address)?.text = address
            v.findViewById<TextView>(R.id.gym_distance)?.text = distance
            v.findViewById<TextView>(R.id.gym_hours)?.text = hours
        }
    }
}
