package com.example.sendbeta.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.sendbeta.R

class CaptureFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_capture, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Back button
        view.findViewById<ImageButton>(R.id.btn_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        // Set up record button
        view.findViewById<View>(R.id.btn_record)?.setOnClickListener {
            Toast.makeText(context, "Recording started...", Toast.LENGTH_SHORT).show()
        }
        
        // Set up flip camera button
        view.findViewById<View>(R.id.btn_flip)?.setOnClickListener {
            Toast.makeText(context, "Camera flipped", Toast.LENGTH_SHORT).show()
        }
        
        // Set up gallery button
        view.findViewById<View>(R.id.btn_gallery)?.setOnClickListener {
            Toast.makeText(context, "Opening gallery...", Toast.LENGTH_SHORT).show()
        }
    }
}
