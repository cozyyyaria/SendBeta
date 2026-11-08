package com.example.sendbeta.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.sendbeta.analysis.Annotation
import com.example.sendbeta.analysis.AnnotationType
import com.example.sendbeta.analysis.NormPoint
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Draws the analysis on top of the video:
 *  - the climber's skeleton (from ML Kit pose detection)
 *  - the hip movement path across the clip
 *  - annotation markers (Start, Crux, Finish, Foot, Rest, Heel hook, Dyno, Note) that
 *    fade in around the moment they were placed and fade out afterwards
 *
 * Everything is stored as fractions of the video frame (NormPoint) and mapped into
 * [videoRect], the area where the VideoView actually shows the video, so the drawing
 * stays lined up with the climber at any screen size or aspect ratio.
 */
class BetaAnalyzerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** True until a video is loaded: shows a static design preview. */
    var demoMode = true
        set(value) { field = value; invalidate() }

    /** Where the video is drawn inside this view (set from the VideoView's bounds). */
    var videoRect: RectF? = null
        set(value) { field = value; invalidate() }

    /** Landmarks for the frame on screen (null = no climber found). */
    var pose: Map<Int, NormPoint>? = null
        set(value) { field = value; invalidate() }

    /** Hip-centre path across the whole clip. */
    var trajectory: List<NormPoint> = emptyList()
        set(value) { field = value; invalidate() }

    /** Markers placed by the user (each tied to a moment in the video). */
    var annotations: List<Annotation> = emptyList()
        set(value) { field = value; invalidate() }

    /** Current playback position; decides which markers are visible. */
    var currentTimeMs: Long = 0L
        set(value) { field = value; invalidate() }

    /** When set, the next tap on the video places a marker of this type. */
    var placingType: AnnotationType? = null
        set(value) { field = value; invalidate() }

    var onPlaced: ((AnnotationType, NormPoint) -> Unit)? = null

    // ---------- Paints ----------
    private val limeColor = Color.parseColor("#D9FF52")
    private val inkColor = Color.parseColor("#18201D")
    private val goldColor = Color.parseColor("#EDAF27")
    private val density = resources.displayMetrics.density

    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = limeColor; style = Paint.Style.FILL }
    private val jointOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkColor; style = Paint.Style.STROKE; strokeWidth = 1.5f * density
    }
    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255); style = Paint.Style.STROKE
        strokeWidth = 2.5f * density; strokeCap = Paint.Cap.ROUND
    }
    private val trajectoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = goldColor; style = Paint.Style.STROKE; strokeWidth = 3f * density
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
        strokeJoin = Paint.Join.ROUND
    }
    private val pinBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pinTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 10f * density; isFakeBoldText = true; textAlign = Paint.Align.CENTER
    }
    private val dimPaint = Paint().apply { color = Color.argb(90, 0, 0, 0) }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 13f * density; isFakeBoldText = true; textAlign = Paint.Align.CENTER
    }
    private val trajectoryPath = Path()

    // ML Kit landmark connections that make up the skeleton
    private val bones = listOf(
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
        PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
        PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
        PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
        PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
        PoseLandmark.LEFT_WRIST to PoseLandmark.LEFT_INDEX,
        PoseLandmark.RIGHT_WRIST to PoseLandmark.RIGHT_INDEX,
        PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_FOOT_INDEX,
        PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_FOOT_INDEX
    )
    private val joints = listOf(
        PoseLandmark.NOSE, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW, PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
        PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
    )

    private fun area(): RectF = videoRect?.takeIf { it.width() > 0 && it.height() > 0 }
        ?: RectF(0f, 0f, width.toFloat(), height.toFloat())

    private fun RectF.px(p: NormPoint) = left + p.x * width()
    private fun RectF.py(p: NormPoint) = top + p.y * height()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        if (demoMode) { drawDemo(canvas); return }

        val r = area()

        // 1. Hip movement path across the clip
        if (trajectory.size >= 2) {
            trajectoryPath.reset()
            trajectoryPath.moveTo(r.px(trajectory[0]), r.py(trajectory[0]))
            trajectory.drop(1).forEach { trajectoryPath.lineTo(r.px(it), r.py(it)) }
            canvas.drawPath(trajectoryPath, trajectoryPaint)
        }

        // 2. Skeleton for the current frame
        pose?.let { lm ->
            bones.forEach { (a, b) ->
                val p1 = lm[a]; val p2 = lm[b]
                if (p1 != null && p2 != null) canvas.drawLine(r.px(p1), r.py(p1), r.px(p2), r.py(p2), bonePaint)
            }
            val radius = 4f * density
            joints.forEach { j ->
                lm[j]?.let {
                    canvas.drawCircle(r.px(it), r.py(it), radius, jointPaint)
                    canvas.drawCircle(r.px(it), r.py(it), radius, jointOutlinePaint)
                }
            }
        }

        // 3. Markers: only around the moment they belong to (fade in / fade out)
        annotations.forEach { a ->
            val alpha = a.alphaAt(currentTimeMs)
            if (alpha > 0.01f) drawPin(canvas, r.px(a.point), r.py(a.point), a.pinLabel(), a.type.color, alpha)
        }

        // 4. Placement hint
        placingType?.let { type ->
            canvas.drawRect(r, dimPaint)
            val what = if (type == AnnotationType.NOTE) "where the note goes" else "the ${type.label.lowercase()} spot"
            canvas.drawText("Tap $what", r.centerX(), r.top + 40f * density, hintPaint)
        }
    }

    private val pinRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1.5f * density
    }

    /** Pin with a dot at the exact tapped point and a label above it. */
    private fun drawPin(canvas: Canvas, x: Float, y: Float, label: String, color: Int, alpha: Float = 1f) {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        pinBackgroundPaint.color = color; pinBackgroundPaint.alpha = a
        pinRingPaint.alpha = a
        pinTextPaint.alpha = a
        canvas.drawCircle(x, y, 7f * density, pinBackgroundPaint)
        canvas.drawCircle(x, y, 7f * density, pinRingPaint)

        val textWidth = pinTextPaint.measureText(label)
        val padH = 6f * density
        val rect = RectF(x - textWidth / 2 - padH, y - 30f * density, x + textWidth / 2 + padH, y - 13f * density)
        canvas.drawRoundRect(rect, 8f * density, 8f * density, pinBackgroundPaint)
        canvas.drawText(label, x, rect.bottom - 5f * density, pinTextPaint)
        pinTextPaint.alpha = 255
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val type = placingType ?: return false // let taps fall through when not placing
        val r = area()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                if (!r.contains(event.x, event.y)) return true // must tap on the video itself
                val p = NormPoint((event.x - r.left) / r.width(), (event.y - r.top) / r.height())
                placingType = null
                onPlaced?.invoke(type, p)
                return true
            }
        }
        return true
    }

    // ---------- Static design preview (no video loaded) ----------
    private fun drawDemo(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        trajectoryPath.reset()
        trajectoryPath.moveTo(w * 0.42f, h * 0.78f)
        trajectoryPath.cubicTo(w * 0.46f, h * 0.65f, w * 0.38f, h * 0.52f, w * 0.48f, h * 0.42f)
        trajectoryPath.cubicTo(w * 0.52f, h * 0.35f, w * 0.44f, h * 0.28f, w * 0.50f, h * 0.22f)
        canvas.drawPath(trajectoryPath, trajectoryPaint)

        val pts = mapOf(
            "head" to (0.48f to 0.32f), "neck" to (0.48f to 0.37f),
            "ls" to (0.40f to 0.38f), "rs" to (0.56f to 0.38f), "le" to (0.32f to 0.28f), "re" to (0.62f to 0.30f),
            "lw" to (0.28f to 0.20f), "rw" to (0.68f to 0.20f), "lh" to (0.43f to 0.52f), "rh" to (0.53f to 0.52f),
            "lk" to (0.38f to 0.66f), "rk" to (0.58f to 0.64f), "la" to (0.36f to 0.80f), "ra" to (0.60f to 0.78f)
        ).mapValues { (_, v) -> (v.first * w) to (v.second * h) }
        listOf("head" to "neck", "neck" to "ls", "neck" to "rs", "ls" to "le", "le" to "lw", "rs" to "re",
            "re" to "rw", "ls" to "lh", "rs" to "rh", "lh" to "rh", "lh" to "lk", "lk" to "la", "rh" to "rk", "rk" to "ra")
            .forEach { (a, b) ->
                val p1 = pts.getValue(a); val p2 = pts.getValue(b)
                canvas.drawLine(p1.first, p1.second, p2.first, p2.second, bonePaint)
            }
        pts.values.forEach {
            canvas.drawCircle(it.first, it.second, 4f * density, jointPaint)
            canvas.drawCircle(it.first, it.second, 4f * density, jointOutlinePaint)
        }
        drawPin(canvas, w * 0.36f, h * 0.82f, "START", AnnotationType.START.color)
        drawPin(canvas, w * 0.28f, h * 0.20f, "CRUX", AnnotationType.CRUX.color)
        drawPin(canvas, w * 0.50f, h * 0.15f, "FINISH", AnnotationType.TOP.color)
    }
}
