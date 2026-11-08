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
import com.example.sendbeta.analysis.Annotation
import com.example.sendbeta.analysis.AnnotationType
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
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
    private val annotations = mutableListOf<Annotation>()
    private var annotationsChanged = false

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            val vv = videoView ?: return
            if (hasVideo) {
                val pos = vv.currentPosition
                view?.findViewById<SeekBar>(R.id.scrub_bar)?.progress = pos
                view?.findViewById<TextView>(R.id.time_current)?.text = formatTime(pos)
                // Smooth skeleton: blended between analysed frames; markers fade by time
                overlay?.currentTimeMs = pos.toLong()
                clipAnalysis?.poseAt(pos.toLong())?.let { overlay?.pose = it }
            }
            progressHandler.postDelayed(this, 33) // ~30 fps
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

        // Annotation tools: pick a marker, then tap the spot on the paused video
        setUpMarkerRow(view, R.id.row_start, AnnotationType.START)
        setUpMarkerRow(view, R.id.row_crux, AnnotationType.CRUX)
        setUpMarkerRow(view, R.id.row_top, AnnotationType.TOP)
        setUpMarkerChips(view)
        overlay?.onPlaced = { type, point -> onMarkerPlaced(type, point) }
        refreshAnnotations()

        view.findViewById<ImageButton>(R.id.btn_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Done: save a new attempt to the database (or just close a saved one)
        view.findViewById<ImageButton>(R.id.btn_done)?.setOnClickListener {
            val id = savedAttemptId
            if (id == null) {
                showSaveDialog()
            } else if (annotationsChanged) {
                val appContext = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.updateAnnotations(id, Annotation.toJson(annotations))
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
                    if (fromUser) {
                        videoView?.seekTo(progress)
                        clipAnalysis?.poseAt(progress.toLong())?.let { overlay?.pose = it }
                    }
                    overlay?.currentTimeMs = progress.toLong()
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
                    annotations.clear()
                    annotations.addAll(Annotation.fromJson(a.annotations))
                    refreshAnnotations()
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
        overlay?.apply { demoMode = false; pose = null; trajectory = emptyList(); currentTimeMs = 0L }
        if (source != null && annotations.isNotEmpty()) {
            // A different video: old markers don't belong to it
            annotations.clear()
            annotationsChanged = true
            refreshAnnotations()
        }
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
                annotations = Annotation.toJson(annotations)
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
        overlay?.currentTimeMs = position
        frameJob = viewLifecycleOwner.lifecycleScope.launch {
            val frame = analyzer.analyzeFrame(position)
            // Exact frame if found, otherwise the blended pose
            overlay?.pose = frame?.landmarks ?: clipAnalysis?.poseAt(position)
        }
    }

    // ---------- Annotations ----------

    /** Start / Crux / Finish rows in the card. */
    private fun setUpMarkerRow(view: View, rowId: Int, type: AnnotationType) {
        view.findViewById<LinearLayout>(rowId)?.setOnClickListener { startPlacing(type) }
        view.findViewById<LinearLayout>(rowId)?.setOnLongClickListener {
            if (annotations.removeAll { it.type == type }) {
                annotationsChanged = true
                refreshAnnotations()
                Toast.makeText(context, "${type.label} marker removed", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    /** Chips for the extra marker types (Foot, Rest, Heel hook, Dyno, Note). */
    private fun setUpMarkerChips(view: View) {
        val group = view.findViewById<ChipGroup>(R.id.marker_chips) ?: return
        AnnotationType.values().filter { !it.single }.forEach { type ->
            group.addView(Chip(requireContext()).apply {
                text = "+ ${type.label}"
                isCheckable = false
                setOnClickListener { startPlacing(type) }
            })
        }
    }

    private fun startPlacing(type: AnnotationType) {
        if (!hasVideo) {
            Toast.makeText(context, "Import a video first, then mark it", Toast.LENGTH_SHORT).show()
            return
        }
        val o = overlay ?: return
        val playButton = view?.findViewById<View>(R.id.btn_play_center)
        if (o.placingType == type) { // tap again = cancel
            o.placingType = null
            playButton?.visibility = View.VISIBLE
            return
        }
        if (isPlaying) togglePlay() // markers are placed on a paused frame
        o.placingType = type
        playButton?.visibility = View.GONE // so the whole video can be tapped
        (view as? android.widget.ScrollView)?.smoothScrollTo(0, 0) // make sure the video is visible
    }

    private fun onMarkerPlaced(type: AnnotationType, point: NormPoint) {
        view?.findViewById<View>(R.id.btn_play_center)?.visibility = View.VISIBLE
        val time = videoView?.currentPosition?.toLong() ?: 0L
        if (type == AnnotationType.NOTE) {
            // Ask for the note text
            val input = TextInputEditText(requireContext()).apply { hint = "e.g. drop knee here" }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add a note")
                .setView(android.widget.FrameLayout(requireContext()).apply {
                    setPadding(48, 16, 48, 0); addView(input)
                })
                .setPositiveButton("Add") { _, _ ->
                    val text = input.text?.toString()?.trim().orEmpty()
                    if (text.isNotEmpty()) addAnnotation(type, point, time, text)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            addAnnotation(type, point, time, null)
        }
    }

    private fun addAnnotation(type: AnnotationType, point: NormPoint, time: Long, text: String?) {
        if (type.single) annotations.removeAll { it.type == type } // Start/Crux/Finish: one each (moves it)
        val id = (annotations.maxOfOrNull { it.id } ?: 0L) + 1
        annotations.add(Annotation(id, type, point, time, text))
        annotations.sortBy { it.timeMs }
        annotationsChanged = true
        refreshAnnotations()
        Toast.makeText(context, "${type.label} marked at ${formatTime(time.toInt())}", Toast.LENGTH_SHORT).show()
    }

    /** Updates the overlay, the Start/Crux/Finish rows and the annotation list. */
    private fun refreshAnnotations() {
        val v = view ?: return
        overlay?.annotations = annotations.toList()

        listOf(
            Triple(AnnotationType.START, R.id.sub_start, R.id.icon_start) to "Opening position",
            Triple(AnnotationType.CRUX, R.id.sub_crux, R.id.icon_crux) to "Hardest move",
            Triple(AnnotationType.TOP, R.id.sub_top, R.id.icon_top) to "Finish position"
        ).forEach { (ids, default) ->
            val (type, subId, iconId) = ids
            val placed = annotations.firstOrNull { it.type == type }
            v.findViewById<TextView>(subId)?.text =
                if (placed != null) "At ${formatTime(placed.timeMs.toInt())} ✓ · tap to move, hold to remove" else default
            v.findViewById<ImageView>(iconId)?.apply {
                setImageResource(if (placed != null) R.drawable.ic_check else R.drawable.ic_plus)
                setColorFilter(ContextCompat.getColor(requireContext(), if (placed != null) R.color.green else R.color.muted))
            }
        }

        val list = v.findViewById<LinearLayout>(R.id.annotation_list) ?: return
        list.removeAllViews()
        v.findViewById<View>(R.id.annotation_empty)?.visibility = if (annotations.isEmpty()) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(requireContext())
        annotations.forEach { a ->
            val row = inflater.inflate(R.layout.item_annotation, list, false)
            row.findViewById<View>(R.id.annotation_dot).background =
                android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(a.type.color)
                }
            row.findViewById<TextView>(R.id.annotation_time).text = formatTime(a.timeMs.toInt())
            row.findViewById<TextView>(R.id.annotation_label).text =
                if (a.type == AnnotationType.NOTE) "Note: ${a.text}" else a.type.label
            row.setOnClickListener { jumpTo(a.timeMs) }
            row.findViewById<ImageButton>(R.id.annotation_delete).setOnClickListener {
                annotations.remove(a)
                annotationsChanged = true
                refreshAnnotations()
            }
            list.addView(row)
        }
    }

    /** Pauses and jumps the video to a marker's moment. */
    private fun jumpTo(timeMs: Long) {
        val vv = videoView ?: return
        if (isPlaying) togglePlay()
        vv.seekTo(timeMs.toInt())
        view?.findViewById<SeekBar>(R.id.scrub_bar)?.progress = timeMs.toInt()
        overlay?.currentTimeMs = timeMs
        clipAnalysis?.poseAt(timeMs)?.let { overlay?.pose = it }
        analyzeCurrentFrame()
        (view as? android.widget.ScrollView)?.smoothScrollTo(0, 0)
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
