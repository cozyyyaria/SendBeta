package com.example.sendbeta.ui

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.sendbeta.R
import com.example.sendbeta.data.AttemptWithRoute
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels()

    // Rotating thumbnail backgrounds for the attempt cards
    private val thumbnails = listOf(R.drawable.bg_attempt_overhang, R.drawable.bg_attempt_cave, R.drawable.bg_attempt_slab)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.home_date)?.text =
            SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())

        // ----- Stats from the database -----
        viewModel.totalSends.observe(viewLifecycleOwner) { count ->
            view.findViewById<TextView>(R.id.stat_sends)?.text = String.format(Locale.US, "%02d", count)
        }
        viewModel.sendsThisWeek.observe(viewLifecycleOwner) { week ->
            view.findViewById<TextView>(R.id.stat_sends_week)?.text = "+$week"
            view.findViewById<TextView>(R.id.hero_subtitle)?.text = when (week) {
                0 -> "No sends yet this week. Time to project something new."
                1 -> "One send this week. Keep the momentum going."
                else -> "$week sends this week. One move closer to your next grade."
            }
        }
        viewModel.activeProjects.observe(viewLifecycleOwner) { count ->
            view.findViewById<TextView>(R.id.stat_projects)?.text = String.format(Locale.US, "%02d", count)
            view.findViewById<TextView>(R.id.stat_projects_sub)?.text = "in progress"
        }
        viewModel.topGrade.observe(viewLifecycleOwner) { grade ->
            view.findViewById<TextView>(R.id.stat_top_grade)?.text = grade
        }
        view.findViewById<TextView>(R.id.stats_updated)?.text = "Live"

        // ----- Recent attempts from the database -----
        viewModel.recentAttempts.observe(viewLifecycleOwner) { attempts -> bindRecent(view, attempts) }

        // ----- Navigation -----
        view.findViewById<TextView>(R.id.btn_avatar)?.setOnClickListener { open(AboutFragment()) }
        view.findViewById<Button>(R.id.btn_record_beta)?.setOnClickListener { open(CaptureFragment()) }
        view.findViewById<TextView>(R.id.btn_view_all_routes)?.setOnClickListener { open(RoutesFragment()) }
    }

    private fun bindRecent(view: View, attempts: List<AttemptWithRoute>) {
        val container = view.findViewById<LinearLayout>(R.id.recent_attempts) ?: return
        container.removeAllViews()
        view.findViewById<View>(R.id.recent_empty)?.visibility = if (attempts.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(requireContext())
        attempts.forEachIndexed { index, a ->
            val card = inflater.inflate(R.layout.item_home_attempt, container, false)
            card.findViewById<View>(R.id.home_attempt_bg).setBackgroundResource(thumbnails[index % thumbnails.size])
            card.findViewById<TextView>(R.id.home_attempt_name).text = a.routeName
            card.findViewById<TextView>(R.id.home_attempt_gym).text = a.gymLabel
            card.findViewById<TextView>(R.id.home_attempt_duration).text =
                if (a.durationMs > 0) RouteUi.seconds(a.durationMs) else "log"
            card.findViewById<TextView>(R.id.home_attempt_grade).apply {
                text = a.grade
                setBackgroundResource(RouteUi.gradeBadgeRes(a.color))
            }
            card.findViewById<TextView>(R.id.home_attempt_badge).apply {
                if (a.sent) {
                    text = "Sent"
                    setBackgroundResource(R.drawable.badge_sent)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check_small, 0, 0, 0)
                    setTextColor(Color.parseColor("#54700C"))
                    compoundDrawableTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#54700C"))
                } else {
                    text = "Fall"
                    setBackgroundResource(R.drawable.badge_fall)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_close, 0, 0, 0)
                    setTextColor(Color.parseColor("#C04E49"))
                    compoundDrawableTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#C04E49"))
                }
            }
            card.setOnClickListener {
                val videoUri = a.videoPath?.let { File(it) }?.takeIf { it.exists() }?.let { Uri.fromFile(it) }
                open(AnalyzerFragment.forSavedAttempt(a.id, videoUri))
            }
            container.addView(card)
        }
    }

    private fun open(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}
