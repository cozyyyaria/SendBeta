package com.example.sendbeta.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.sendbeta.R
import java.util.Locale

class AnalyzerFragment : Fragment() {

    private var isPlaying = false

    // Playback speeds the speed button cycles through
    private val speeds = listOf("0.5×", "1×", "2×")
    private var speedIndex = 1 // starts at 1×

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

        // Play / pause: both buttons share one state and both icons update together
        val iconCenter = view.findViewById<ImageView>(R.id.icon_play_center)
        val iconSmall = view.findViewById<ImageView>(R.id.icon_play_small)

        fun togglePlay() {
            isPlaying = !isPlaying
            val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            iconCenter?.setImageResource(icon)
            iconSmall?.setImageResource(icon)
            val message = if (isPlaying) "Playing video" else "Paused"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<FrameLayout>(R.id.btn_play_center)?.setOnClickListener { togglePlay() }
        view.findViewById<FrameLayout>(R.id.btn_play_small)?.setOnClickListener { togglePlay() }

        // Speed button: cycles 0.5× → 1× → 2× and shows the current speed
        val speedButton = view.findViewById<TextView>(R.id.btn_speed)
        speedButton?.text = speeds[speedIndex]
        speedButton?.setOnClickListener {
            speedIndex = (speedIndex + 1) % speeds.size
            speedButton.text = speeds[speedIndex]
            Toast.makeText(context, "Speed: ${speeds[speedIndex]}", Toast.LENGTH_SHORT).show()
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
