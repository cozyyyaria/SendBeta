package com.example.sendbeta.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/** A point as a fraction of the video frame (0..1), so it lines up at any screen size. */
data class NormPoint(val x: Float, val y: Float) {
    fun serialize(): String = "$x,$y"

    companion object {
        fun parse(s: String?): NormPoint? = s?.split(",")?.takeIf { it.size == 2 }?.let {
            NormPoint(it[0].toFloat(), it[1].toFloat())
        }
    }
}

/** Body landmarks found in one video frame (keyed by ML Kit PoseLandmark type). */
data class PoseFrame(val timeMs: Long, val landmarks: Map<Int, NormPoint>) {
    /** Centre of the hips = the climber's centre of mass, used for the movement path. */
    fun hipCenter(): NormPoint? {
        val l = landmarks[PoseLandmark.LEFT_HIP] ?: return null
        val r = landmarks[PoseLandmark.RIGHT_HIP] ?: return null
        return NormPoint((l.x + r.x) / 2f, (l.y + r.y) / 2f)
    }

    /** Highest hand position in this frame (smaller y = higher up). */
    fun highestHandY(): Float? = listOfNotNull(
        landmarks[PoseLandmark.LEFT_WRIST]?.y, landmarks[PoseLandmark.RIGHT_WRIST]?.y
    ).minOrNull()
}

/**
 * Real pose analysis for a climbing video:
 * pulls frames from the video with MediaMetadataRetriever and runs Google ML Kit
 * Pose Detection (on-device, 33 body landmarks) on each one.
 */
class PoseAnalyzer(private val context: Context) : Closeable {

    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
    )
    private var retriever: MediaMetadataRetriever? = null
    private var rotation = 0
    private var expectPortrait = false
    private val lock = Mutex() // MediaMetadataRetriever is not thread-safe

    suspend fun open(uri: Uri) = withContext(Dispatchers.IO) {
        lock.withLock {
            retriever?.release()
            retriever = MediaMetadataRetriever().apply { setDataSource(context, uri) }.also { r ->
                rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                // How the video looks on screen after applying its rotation
                expectPortrait = if (rotation == 90 || rotation == 270) w > h else h > w
            }
        }
    }

    /** Detects the climber's pose in the frame at [timeMs]. Returns null if nobody is found. */
    suspend fun analyzeFrame(timeMs: Long): PoseFrame? = withContext(Dispatchers.Default) {
        val bitmap = lock.withLock {
            val r = retriever ?: return@withContext null
            try {
                r.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
            } catch (e: Exception) {
                null
            }
        } ?: return@withContext null

        val frame = prepare(bitmap)
        val pose = try {
            detector.process(InputImage.fromBitmap(frame, 0)).await()
        } catch (e: Exception) {
            return@withContext null
        }

        val landmarks = pose.allPoseLandmarks
            .filter { it.inFrameLikelihood > 0.5f }
            .associate { it.landmarkType to NormPoint(it.position.x / frame.width, it.position.y / frame.height) }
        if (frame !== bitmap) frame.recycle()
        bitmap.recycle()
        if (landmarks.size < 6) null else PoseFrame(timeMs, landmarks)
    }

    /** Samples the whole clip (up to 40 frames) to trace how the climber moved. */
    suspend fun analyzeClip(durationMs: Long, onProgress: (Int) -> Unit): ClipAnalysis {
        val samples = min(40, max(8, (durationMs / 250).toInt()))
        val step = durationMs / samples
        val frames = mutableListOf<PoseFrame>()
        for (i in 0 until samples) {
            analyzeFrame(i * step)?.let { frames.add(it) }
            withContext(Dispatchers.Main) { onProgress(((i + 1) * 100) / samples) }
        }
        return ClipAnalysis(samples, frames)
    }

    /** Scales the frame down (faster detection) and fixes its rotation if needed. */
    private fun prepare(src: Bitmap): Bitmap {
        val scale = min(1f, 480f / max(src.width, src.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            val isPortrait = src.height > src.width
            if (rotation != 0 && isPortrait != expectPortrait) postRotate(rotation.toFloat())
        }
        return if (matrix.isIdentity) src else Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /** Releases the video and model once any frame being read has finished. */
    override fun close() {
        CoroutineScope(Dispatchers.IO).launch {
            lock.withLock {
                try { retriever?.release() } catch (_: Exception) {}
                retriever = null
            }
            detector.close()
        }
    }
}

/** Results of analysing a whole clip. */
data class ClipAnalysis(val sampledFrames: Int, val frames: List<PoseFrame>) {

    /**
     * Frames with jitter removed: each landmark is averaged with the same landmark
     * in the previous and next frame (weights 1-2-1), so the skeleton doesn't shake.
     */
    private val smoothed: List<PoseFrame> by lazy {
        frames.mapIndexed { i, f ->
            val prev = frames.getOrNull(i - 1)
            val next = frames.getOrNull(i + 1)
            PoseFrame(f.timeMs, f.landmarks.mapValues { (type, p) ->
                var sx = p.x * 2; var sy = p.y * 2; var w = 2f
                prev?.landmarks?.get(type)?.let { sx += it.x; sy += it.y; w += 1f }
                next?.landmarks?.get(type)?.let { sx += it.x; sy += it.y; w += 1f }
                NormPoint(sx / w, sy / w)
            })
        }
    }

    val hipPath: List<NormPoint> get() = smoothed.mapNotNull { it.hipCenter() }

    /** Hip path points with their times, for drawing the trail up to the current moment. */
    val timedHipPath: List<Pair<Long, NormPoint>> get() = smoothed.mapNotNull { f -> f.hipCenter()?.let { f.timeMs to it } }

    /** Closest analysed frame to a playback position. */
    fun frameAt(timeMs: Long): PoseFrame? = smoothed.minByOrNull { kotlin.math.abs(it.timeMs - timeMs) }

    /**
     * Skeleton at any moment of the video: blends the two analysed frames around [timeMs]
     * so the skeleton glides with the climber instead of jumping between samples.
     */
    fun poseAt(timeMs: Long): Map<Int, NormPoint>? {
        if (smoothed.isEmpty()) return null
        val after = smoothed.indexOfFirst { it.timeMs >= timeMs }
        if (after <= 0) return (if (after == 0) smoothed[0] else smoothed.last()).landmarks
        val a = smoothed[after - 1]
        val b = smoothed[after]
        val span = (b.timeMs - a.timeMs).coerceAtLeast(1)
        if (span > 2000) return (if (timeMs - a.timeMs < b.timeMs - timeMs) a else b).landmarks // big gap: no blending
        val t = ((timeMs - a.timeMs).toFloat() / span).coerceIn(0f, 1f)
        return a.landmarks.mapNotNull { (type, pa) ->
            val pb = b.landmarks[type] ?: return@mapNotNull (if (t < 0.5f) type to pa else null)
            type to NormPoint(pa.x + (pb.x - pa.x) * t, pa.y + (pb.y - pa.y) * t)
        }.toMap()
    }

    fun summary(durationMs: Long): String {
        if (frames.isEmpty()) return "No climber detected. Try a clip where the whole body is visible."
        val coverage = frames.size * 100 / sampledFrames
        val path = hipPath
        val climbedPct = if (path.size >= 2) ((path.first().y - path.minOf { it.y }) * 100).toInt().coerceAtLeast(0) else 0
        val reachPct = frames.mapNotNull { it.highestHandY() }.minOrNull()?.let { ((1 - it) * 100).toInt() } ?: 0
        return "Pose detected in ${frames.size} of $sampledFrames frames ($coverage%) over ${durationMs / 1000}s.\n" +
            "Hips moved up $climbedPct% of the frame · highest reach at $reachPct% of the frame height."
    }
}

/** Await an ML Kit Task from a coroutine. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
