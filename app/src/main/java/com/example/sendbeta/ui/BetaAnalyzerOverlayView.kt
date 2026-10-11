package com.example.sendbeta.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class BetaAnalyzerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var showSkeleton: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var showTrajectory: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    // Colors
    private val limeColor = Color.parseColor("#D9FF52")
    private val coralColor = Color.parseColor("#FF7F66")
    private val greenColor = Color.parseColor("#1A7F55")
    private val goldColor = Color.parseColor("#EDAF27")
    private val inkColor = Color.parseColor("#18201D")

    private val trajectoryPath = Path()

    // Paints
    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = limeColor
        style = Paint.Style.FILL
    }

    private val jointOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkColor
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val trajectoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = goldColor
        style = Paint.Style.STROKE
        strokeWidth = 8f
        pathEffect = DashPathEffect(floatArrayOf(16f, 12f), 0f)
    }

    private val pinBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pinTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        // 1. Draw Trajectory Path (Hip Center-of-Mass movement)
        if (showTrajectory) {
            trajectoryPath.reset()
            trajectoryPath.moveTo(w * 0.42f, h * 0.78f)
            trajectoryPath.cubicTo(
                w * 0.46f, h * 0.65f,
                w * 0.38f, h * 0.52f,
                w * 0.48f, h * 0.42f
            )
            trajectoryPath.cubicTo(
                w * 0.52f, h * 0.35f,
                w * 0.44f, h * 0.28f,
                w * 0.50f, h * 0.22f
            )
            canvas.drawPath(trajectoryPath, trajectoryPaint)
        }

        // 2. Draw Pose Skeleton (33 ML Kit Keypoints mock skeleton)
        if (showSkeleton) {
            // Skeleton joint coordinates (normalized)
            val head = Pair(w * 0.48f, h * 0.32f)
            val neck = Pair(w * 0.48f, h * 0.37f)
            val leftShoulder = Pair(w * 0.40f, h * 0.38f)
            val rightShoulder = Pair(w * 0.56f, h * 0.38f)
            val leftElbow = Pair(w * 0.32f, h * 0.28f)
            val rightElbow = Pair(w * 0.62f, h * 0.30f)
            val leftWrist = Pair(w * 0.28f, h * 0.20f) // Reaching start/crux hold
            val rightWrist = Pair(w * 0.68f, h * 0.20f)
            val hipCenter = Pair(w * 0.48f, h * 0.52f)
            val leftHip = Pair(w * 0.43f, h * 0.52f)
            val rightHip = Pair(w * 0.53f, h * 0.52f)
            val leftKnee = Pair(w * 0.38f, h * 0.66f)
            val rightKnee = Pair(w * 0.58f, h * 0.64f)
            val leftAnkle = Pair(w * 0.36f, h * 0.80f)
            val rightAnkle = Pair(w * 0.60f, h * 0.78f)

            // Draw Bones (Lines)
            val bones = listOf(
                Pair(head, neck),
                Pair(neck, leftShoulder), Pair(neck, rightShoulder),
                Pair(leftShoulder, leftElbow), Pair(leftElbow, leftWrist),
                Pair(rightShoulder, rightElbow), Pair(rightElbow, rightWrist),
                Pair(leftShoulder, leftHip), Pair(rightShoulder, rightHip),
                Pair(leftHip, rightHip),
                Pair(leftHip, leftKnee), Pair(leftKnee, leftAnkle),
                Pair(rightHip, rightKnee), Pair(rightKnee, rightAnkle)
            )

            for ((p1, p2) in bones) {
                canvas.drawLine(p1.first, p1.second, p2.first, p2.second, bonePaint)
            }

            // Draw Joints (Circles)
            val joints = listOf(
                head, neck, leftShoulder, rightShoulder, leftElbow, rightElbow,
                leftWrist, rightWrist, hipCenter, leftHip, rightHip,
                leftKnee, rightKnee, leftAnkle, rightAnkle
            )

            for (j in joints) {
                canvas.drawCircle(j.first, j.second, 12f, jointPaint)
                canvas.drawCircle(j.first, j.second, 12f, jointOutlinePaint)
            }
        }

        // 3. Draw Hold Pins / Markers
        drawHoldPin(canvas, w * 0.36f, h * 0.82f, "START", greenColor)
        drawHoldPin(canvas, w * 0.28f, h * 0.20f, "CRUX", coralColor)
        drawHoldPin(canvas, w * 0.50f, h * 0.15f, "TOP", goldColor)
    }

    private fun drawHoldPin(canvas: Canvas, x: Float, y: Float, label: String, color: Int) {
        val paddingH = 20f
        val textWidth = pinTextPaint.measureText(label)
        val rectLeft = x - textWidth / 2f - paddingH
        val rectTop = y - 20f
        val rectRight = x + textWidth / 2f + paddingH
        val rectBottom = y + 20f

        pinBackgroundPaint.color = color
        val rect = RectF(rectLeft, rectTop, rectRight, rectBottom)
        canvas.drawRoundRect(rect, 20f, 20f, pinBackgroundPaint)

        canvas.drawText(label, x, y + 8f, pinTextPaint)
    }
}
