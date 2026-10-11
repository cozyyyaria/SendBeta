package com.example.sendbeta.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.sendbeta.R
import java.util.Locale

class AnalyzerFragment : Fragment() {

    private var isPlaying = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_analyzer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val overlayCanvas = view.findViewById<BetaAnalyzerOverlayView>(R.id.overlay_canvas)

        // Back button
        view.findViewById<ImageButton>(R.id.btn_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Done/Save button
        view.findViewById<ImageButton>(R.id.btn_done)?.setOnClickListener {
            Toast.makeText(context, "Analysis saved!", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }

        // Center Play button (on video)
        view.findViewById<FrameLayout>(R.id.btn_play_center)?.setOnClickListener {
            isPlaying = !isPlaying
            if (isPlaying) {
                Toast.makeText(context, "Playing video", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Paused", Toast.LENGTH_SHORT).show()
            }
        }

        // Small Play button (in controls)
        view.findViewById<FrameLayout>(R.id.btn_play_small)?.setOnClickListener {
            isPlaying = !isPlaying
            if (isPlaying) {
                Toast.makeText(context, "Playing video", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Paused", Toast.LENGTH_SHORT).show()
            }
        }

        // Speed button
        view.findViewById<TextView>(R.id.btn_speed)?.setOnClickListener {
            Toast.makeText(context, "Speed: 0.5x", Toast.LENGTH_SHORT).show()
        }

        // Scrub bar
        val timeCurrentText = view.findViewById<TextView>(R.id.time_current)
        view.findViewById<SeekBar>(R.id.scrub_bar)?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val currentSec = progress / 10
                val formattedTime = String.format(Locale.US, "00:%02d", currentSec)
                timeCurrentText?.text = formattedTime
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}
