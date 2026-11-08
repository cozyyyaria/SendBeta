package com.example.sendbeta.ui

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import android.graphics.RectF
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.sendbeta.R
import com.example.sendbeta.analysis.ClipAnalysis
import com.example.sendbeta.analysis.NormPoint
import com.example.sendbeta.analysis.PoseAnalyzer
import com.example.sendbeta.data.SendBetaRepository
import com.example.sendbeta.ui.BetaAnalyzerOverlayView.Hold
import kotlinx.coroutines.Job
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnalyzerFragment : Fragment() {

    companion object {
        private const val ARG_VIDEO_URI = "video_uri"
        private const val ARG_SOURCE = "source"
        private const val ARG_ROUTE_ID = "route_id"
        private const val ARG_ATTEMPT_ID = "attempt_id"

        /** New video (from the gallery or the camera), optionally for a known route. */
        fun newInstance(videoUri: Uri, source: String, routeId: Long? = null): AnalyzerFragment =
            AnalyzerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_VIDEO_URI, videoUri.toString())
                    putString(ARG_SOURCE, source)
                    routeId?.let { putLong(ARG_ROUTE_ID, it) }
                }
            }

        /** Re-open an attempt already saved in the database. */
        fun forSavedAttempt(attemptId: Long, videoUri: Uri?): AnalyzerFragment =
            AnalyzerFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_ATTEMPT_ID, attemptId)
                    videoUri?.let { putString(ARG_VIDEO_URI, it.toString()) }
                }
            }
    }

    private val repo by lazy { SendBetaRepository.get(requireContext()) }

    private var isPlaying = false

    // Playback speeds the speed button cycles through
    private val speeds = listOf(0.5f, 1f, 2f)
    private val speedLabels = listOf("0.5×", "1×", "2×")
    private var speedIndex = 1 // starts at 1×

    private var videoView: VideoView? = null
    private var mediaPlayer: MediaPlayer? = null
    private var hasVideo = false
    private var resumePosition = 0

    // What is being analyzed
    private var currentVideoUri: Uri? = null
    private var currentSource = "Logged"
    private var durationMs = 0L
    private var savedAttemptId: Long? = null   // non-null = already in the database

    // Pose analysis (Google ML Kit) + annotations
    private var overlay: BetaAnalyzerOverlayView? = null
    private var poseAnalyzer: PoseAnalyzer? = null
    private var clipAnalysis: ClipAnalysis? = null
    private var analysisJob: Job? = null
    private var frameJob: Job? = null
    private var holdsChanged = false

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            val vv = videoView ?: return
            if (hasVideo) {
                view?.findViewById<SeekBar>(R.id.scrub_bar)?.progress = vv.currentPosition
                view?.findViewById<TextView>(R.id.time_current)?.text = formatTime(vv.currentPosition)
                // While playing, show the closest analysed frame's skeleton
                clipAnalysis?.frameAt(vv.currentPosition.toLong())?.let { overlay?.pose = it.landmarks }
            }
            progressHandler.postDelayed(this, 200)
        }
    }

    // System file picker filtered to videos (works with files dragged onto the emulator)
    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            savedAttemptId = null // a new video = a new attempt to save
            loadVideo(uri, "Imported video")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_analyzer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        videoView = view.findViewById(R.id.video_view)
        overlay = view.findViewById(R.id.overlay_canvas)
        poseAnalyzer = PoseAnalyzer(requireContext().applicationContext)

        // Keep the overlay lined up with the area where the video is actually shown
        videoView?.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            if (v.visibility == View.VISIBLE) overlay?.videoRect = RectF(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat())
        }

        // Annotation tools: tap a hold type, then tap the hold on the paused video
        setUpHoldRow(view, R.id.row_start, Hold.START)
        setUpHoldRow(view, R.id.row_crux, Hold.CRUX)
        setUpHoldRow(view, R.id.row_top, Hold.TOP)
        overlay?.onHoldPlaced = { hold, _ ->
            holdsChanged = true
            view.findViewById<View>(R.id.btn_play_center)?.visibility = View.VISIBLE
            updateHoldRow(hold)
            Toast.makeText(context, "${hold.label.lowercase().replaceFirstChar { it.uppercase() }} hold placed", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<ImageButton>(R.id.btn_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Done: save a new attempt to the database (or just close a saved one)
        view.findViewById<ImageButton>(R.id.btn_done)?.setOnClickListener {
            val id = savedAttemptId
            if (id == null) {
                showSaveDialog()
            } else if (holdsChanged) {
                val holds = overlay?.holds.orEmpty()
                val appContext = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.updateHolds(id, holds[Hold.START]?.serialize(), holds[Hold.CRUX]?.serialize(), holds[Hold.TOP]?.serialize())
                    Toast.makeText(appContext, "Annotations saved", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
            } else {
                parentFragmentManager.popBackStack()
            }
        }

        view.findViewById<TextView>(R.id.btn_import_video)?.setOnClickListener {
            pickVideo.launch("video/*")
        }

        view.findViewById<FrameLayout>(R.id.btn_play_center)?.setOnClickListener { togglePlay() }
        view.findViewById<FrameLayout>(R.id.btn_play_small)?.setOnClickListener { togglePlay() }

        val speedButton = view.findViewById<TextView>(R.id.btn_speed)
        speedButton?.text = speedLabels[speedIndex]
        speedButton?.setOnClickListener {
            speedIndex = (speedIndex + 1) % speeds.size
            speedButton.text = speedLabels[speedIndex]
            if (isPlaying) applySpeed()
            Toast.makeText(context, "Speed: ${speedLabels[speedIndex]}", Toast.LENGTH_SHORT).show()
        }

        val timeCurrentText = view.findViewById<TextView>(R.id.time_current)
        view.findViewById<SeekBar>(R.id.scrub_bar)?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (hasVideo) {
                    if (fromUser) videoView?.seekTo(progress)
                    timeCurrentText?.text = formatTime(progress)
                } else {
                    timeCurrentText?.text = String.format(Locale.US, "00:%02d", progress / 10)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (hasVideo && !isPlaying) analyzeCurrentFrame()
            }
        })

        val args = arguments
        val attemptId = args?.getLong(ARG_ATTEMPT_ID)?.takeIf { it > 0 }
        if (attemptId != null) {
            // Saved attempt: show its route and result from the database
            savedAttemptId = attemptId
            viewLifecycleOwner.lifecycleScope.launch {
                repo.getAttempt(attemptId)?.let { a ->
                    view.findViewById<TextView>(R.id.analyzer_title)?.text = a.routeName
                    val result = if (a.sent) "Sent" else "Fell"
                    view.findViewById<TextView>(R.id.analyzer_subtitle)?.text =
                        "$result · ${RouteUi.shortDate(a.createdAt)} · ${RouteUi.seconds(a.durationMs)}"
                    overlay?.setHold(Hold.START, NormPoint.parse(a.startHold))
                    overlay?.setHold(Hold.CRUX, NormPoint.parse(a.cruxHold))
                    overlay?.setHold(Hold.TOP, NormPoint.parse(a.topHold))
                    Hold.values().forEach { updateHoldRow(it) }
                    if (a.videoPath == null) {
                        view.findViewById<TextView>(R.id.analysis_summary)?.text =
                            "This attempt was logged without a video. Import one to analyse it."
                    }
                }
            }
            args?.getString(ARG_VIDEO_URI)?.let { loadVideo(Uri.parse(it), null) }
        } else {
            args?.getString(ARG_VIDEO_URI)?.let { uriString ->
                loadVideo(Uri.parse(uriString), args?.getString(ARG_SOURCE) ?: "Imported video")
            }
        }
    }

    /** [source] = null keeps the title/subtitle (used for saved attempts). */
    private fun loadVideo(uri: Uri, source: String?) {
        val v = view ?: return
        val vv = videoView ?: return
        hasVideo = true
        currentVideoUri = uri
        if (source != null) currentSource = source
        setPlaying(false)

        v.findViewById<View>(R.id.video_placeholder)?.visibility = View.GONE
        vv.visibility = View.VISIBLE
        analysisJob?.cancel()
        clipAnalysis = null
        overlay?.apply { demoMode = false; pose = null; trajectory = emptyList() }
        if (source != null) {
            v.findViewById<TextView>(R.id.analyzer_title)?.text = "My Beta"
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            v.findViewById<TextView>(R.id.analyzer_subtitle)?.text = "$source · Today, $time"
        }

        vv.setOnPreparedListener { mp ->
            mediaPlayer = mp
            mp.isLooping = true
            val duration = mp.duration
            durationMs = duration.toLong()
            v.findViewById<SeekBar>(R.id.scrub_bar)?.apply {
                max = duration
                progress = 0
            }
            v.findViewById<TextView>(R.id.time_total)?.text = formatTime(duration)
            v.findViewById<TextView>(R.id.time_current)?.text = formatTime(0)
            vv.seekTo(if (resumePosition > 0) resumePosition else 1) // show the first frame
            if (clipAnalysis == null && analysisJob?.isActive != true) runPoseAnalysis(uri, durationMs)
        }
        vv.setOnErrorListener { _, _, _ ->
            Toast.makeText(context, "Couldn't play this video. Try an .mp4 file.", Toast.LENGTH_LONG).show()
            hasVideo = false
            currentVideoUri = null
            vv.visibility = View.GONE
            v.findViewById<View>(R.id.video_placeholder)?.visibility = View.VISIBLE
            true
        }
        resumePosition = 0
        vv.setVideoURI(uri)
    }

    /** Asks which route + result, then saves the attempt (and a copy of the video) to Room. */
    private fun showSaveDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val routes = repo.getRoutes()
            if (routes.isEmpty()) {
                Toast.makeText(context, "Add a route first in the Routes tab", Toast.LENGTH_LONG).show()
                return@launch
            }
            val dialogView = layoutInflater.inflate(R.layout.dialog_save_attempt, null)
            val spinner = dialogView.findViewById<Spinner>(R.id.spinner_route)
            spinner.adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_dropdown_item,
                routes.map { "${it.name} (${it.grade})" }
            )
            val presetRouteId = arguments?.getLong(ARG_ROUTE_ID)?.takeIf { it > 0 }
            routes.indexOfFirst { it.id == presetRouteId }.takeIf { it >= 0 }?.let { spinner.setSelection(it) }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Save attempt")
                .setView(dialogView)
                .setPositiveButton("Save") { _, _ ->
                    val route = routes[spinner.selectedItemPosition]
                    val sent = dialogView.findViewById<RadioButton>(R.id.radio_sent).isChecked
                    val note = dialogView.findViewById<TextInputEditText>(R.id.input_attempt_note)
                        .text?.toString()?.trim().orEmpty()
                    saveAttempt(route.id, route.name, sent, note)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun saveAttempt(routeId: Long, routeName: String, sent: Boolean, note: String) {
        videoView?.pause()
        setPlaying(false)
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(appContext, "Saving…", Toast.LENGTH_SHORT).show()
            savedAttemptId = repo.saveAttempt(
                routeId = routeId,
                sent = sent,
                note = note,
                durationMs = durationMs,
                videoUri = currentVideoUri,
                source = if (currentVideoUri != null) currentSource else "Logged",
                startHold = overlay?.holds?.get(Hold.START)?.serialize(),
                cruxHold = overlay?.holds?.get(Hold.CRUX)?.serialize(),
                topHold = overlay?.holds?.get(Hold.TOP)?.serialize()
            )
            Toast.makeText(appContext, "Attempt saved to $routeName", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    // ---------- Pose analysis ----------

    /** Runs ML Kit pose detection over the clip, then draws the hip path + skeleton. */
    private fun runPoseAnalysis(uri: Uri, duration: Long) {
        val v = view ?: return
        val analyzer = poseAnalyzer ?: return
        val badge = v.findViewById<TextView>(R.id.pose_badge)
        val summary = v.findViewById<TextView>(R.id.analysis_summary)
        badge?.text = "Analyzing pose… 0%"
        summary?.text = "Detecting the climber's body in each frame…"

        analysisJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                analyzer.open(uri)
                val result = analyzer.analyzeClip(duration) { pct -> badge?.text = "Analyzing pose… $pct%" }
                clipAnalysis = result
                overlay?.trajectory = result.hipPath
                overlay?.pose = result.frameAt(videoView?.currentPosition?.toLong() ?: 0L)?.landmarks
                badge?.text = if (result.frames.isEmpty()) "No climber detected"
                else "Pose tracked · ${result.frames.size}/${result.sampledFrames} frames"
                summary?.text = result.summary(duration)
            } catch (e: Exception) {
                badge?.text = "Pose analysis unavailable"
                summary?.text = "Couldn't analyse this video (${e.javaClass.simpleName}). Playback and hold marking still work."
            }
        }
    }

    /** Precise skeleton for the exact paused frame. */
    private fun analyzeCurrentFrame() {
        val analyzer = poseAnalyzer ?: return
        if (clipAnalysis == null) return // clip pass still running; it will draw when done
        val position = videoView?.currentPosition?.toLong() ?: return
        frameJob?.cancel()
        frameJob = viewLifecycleOwner.lifecycleScope.launch {
            val frame = analyzer.analyzeFrame(position)
            overlay?.pose = frame?.landmarks
        }
    }

    // ---------- Hold annotations ----------

    private fun setUpHoldRow(view: View, rowId: Int, hold: Hold) {
        view.findViewById<LinearLayout>(rowId)?.setOnClickListener {
            if (!hasVideo) {
                Toast.makeText(context, "Import a video first, then mark the holds on it", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val o = overlay ?: return@setOnClickListener
            val playButton = view.findViewById<View>(R.id.btn_play_center)
            if (o.placingHold == hold) { // tap again = cancel
                o.placingHold = null
                playButton?.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (isPlaying) togglePlay() // holds are placed on a paused frame
            o.placingHold = hold
            playButton?.visibility = View.GONE // so the whole video can be tapped
            // scroll up so the video is visible while placing
            (view as? android.widget.ScrollView)?.smoothScrollTo(0, 0)
        }
        // Long-press removes a placed hold
        view.findViewById<LinearLayout>(rowId)?.setOnLongClickListener {
            if (overlay?.holds?.containsKey(hold) == true) {
                overlay?.setHold(hold, null)
                holdsChanged = true
                updateHoldRow(hold)
                Toast.makeText(context, "${hold.label.lowercase()} hold removed", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun updateHoldRow(hold: Hold) {
        val v = view ?: return
        val (subId, iconId, defaultText) = when (hold) {
            Hold.START -> Triple(R.id.sub_start, R.id.icon_start, "Opening position")
            Hold.CRUX -> Triple(R.id.sub_crux, R.id.icon_crux, "Hardest move")
            Hold.TOP -> Triple(R.id.sub_top, R.id.icon_top, "Finish position")
        }
        val placed = overlay?.holds?.containsKey(hold) == true
        v.findViewById<TextView>(subId)?.text = if (placed) "Placed ✓ · tap to move, hold to remove" else defaultText
        v.findViewById<ImageView>(iconId)?.apply {
            setImageResource(if (placed) R.drawable.ic_check else R.drawable.ic_plus)
            setColorFilter(ContextCompat.getColor(requireContext(), if (placed) R.color.green else R.color.muted))
        }
    }

    private fun togglePlay() {
        if (!hasVideo) {
            Toast.makeText(context, "Import a climbing video to play it", Toast.LENGTH_SHORT).show()
            return
        }
        val vv = videoView ?: return
        if (isPlaying) {
            vv.pause()
            setPlaying(false)
            analyzeCurrentFrame()
        } else {
            vv.start()
            applySpeed()
            setPlaying(true)
        }
    }

    private fun setPlaying(playing: Boolean) {
        isPlaying = playing
        val icon = if (playing) R.drawable.ic_pause else R.drawable.ic_play
        view?.findViewById<ImageView>(R.id.icon_play_center)?.setImageResource(icon)
        view?.findViewById<ImageView>(R.id.icon_play_small)?.setImageResource(icon)
        // Hide the big center button while playing so the video is visible
        view?.findViewById<View>(R.id.btn_play_center)?.visibility = if (playing) View.GONE else View.VISIBLE

        progressHandler.removeCallbacks(progressUpdater)
        if (playing) progressHandler.post(progressUpdater)
    }

    /** Changes playback speed (only safe while playing: setting speed starts playback). */
    private fun applySpeed() {
        try {
            mediaPlayer?.let { mp -> mp.playbackParams = mp.playbackParams.setSpeed(speeds[speedIndex]) }
        } catch (_: Exception) {
            // Some codecs/devices don't support speed changes; keep playing at normal speed
        }
    }

    private fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    override fun onPause() {
        super.onPause()
        videoView?.let { vv ->
            if (hasVideo) resumePosition = vv.currentPosition
            if (vv.isPlaying) vv.pause()
        }
        setPlaying(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressHandler.removeCallbacks(progressUpdater)
        videoView?.stopPlayback()
        videoView = null
        mediaPlayer = null
        analysisJob?.cancel()
        frameJob?.cancel()
        poseAnalyzer?.close()
        poseAnalyzer = null
        overlay = null
    }
}
