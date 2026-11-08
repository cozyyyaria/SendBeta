package com.example.sendbeta.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.textfield.TextInputEditText
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.sendbeta.R
import com.example.sendbeta.data.Attempt
import com.example.sendbeta.data.Route
import com.example.sendbeta.data.SendBetaRepository
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class RouteDetailFragment : Fragment() {

    companion object {
        private const val ARG_ROUTE_ID = "route_id"

        fun newInstance(routeId: Long): RouteDetailFragment = RouteDetailFragment().apply {
            arguments = Bundle().apply { putLong(ARG_ROUTE_ID, routeId) }
        }
    }

    private val viewModel: RouteDetailViewModel by viewModels()
    private var currentRoute: Route? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_route_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.load(arguments?.getLong(ARG_ROUTE_ID) ?: 0L)

        viewModel.route.observe(viewLifecycleOwner) { route ->
            if (route == null) return@observe // deleted
            currentRoute = route
            bindRoute(view, route)
        }
        viewModel.attempts.observe(viewLifecycleOwner) { attempts -> bindAttempts(view, attempts) }

        view.findViewById<ImageButton>(R.id.btn_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Edit (and delete) this route
        view.findViewById<ImageButton>(R.id.btn_edit)?.setOnClickListener {
            val route = currentRoute ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                val gyms = SendBetaRepository.get(requireContext()).getGyms()
                RouteUi.showRouteDialog(
                    context = requireContext(),
                    gyms = gyms,
                    existing = route,
                    onSave = { viewModel.updateRoute(it) },
                    onDelete = {
                        // Activity scope: the delete must finish even though this screen closes
                        val repo = SendBetaRepository.get(requireContext())
                        requireActivity().lifecycleScope.launch { repo.deleteRoute(route) }
                        parentFragmentManager.popBackStack()
                    }
                )
            }
        }

        // Share the route as text with any app (Messages, email, etc.)
        view.findViewById<ImageButton>(R.id.btn_share)?.setOnClickListener {
            val route = currentRoute ?: return@setOnClickListener
            val text = "I'm projecting ${route.name} (${route.grade}) at ${route.gymLabel} 🧗 — tracked with SendBeta"
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                "Share route"
            ))
        }

        view.findViewById<Button>(R.id.btn_view_map)?.setOnClickListener {
            val route = currentRoute ?: return@setOnClickListener
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MapFragment.newInstance(route.gymLabel))
                .addToBackStack(null)
                .commit()
        }

        // Log attempt: with a video (Capture) or a quick log without one
        view.findViewById<Button>(R.id.btn_log_attempt)?.setOnClickListener {
            val route = currentRoute ?: return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Log an attempt")
                .setItems(arrayOf("Record or import a video", "Quick log (no video)")) { _, which ->
                    if (which == 0) {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, CaptureFragment.newInstance(route.id))
                            .addToBackStack(null)
                            .commit()
                    } else {
                        showAttemptDialog(route, existing = null)
                    }
                }
                .show()
        }
    }

    private fun bindRoute(view: View, route: Route) {
        view.findViewById<TextView>(R.id.route_detail_title)?.text = route.name
        view.findViewById<TextView>(R.id.route_detail_gym)?.text = route.gymLabel
        view.findViewById<TextView>(R.id.banner_grade)?.text = route.grade
        view.findViewById<TextView>(R.id.profile_grade)?.text = route.grade
        view.findViewById<TextView>(R.id.route_rating)?.text = RouteUi.stars(route.rating)
        view.findViewById<TextView>(R.id.banner_subtitle)?.text =
            route.tagList().joinToString(", ").ifEmpty { "Add style tags with the edit button." }
        view.findViewById<TextView>(R.id.route_notes)?.text =
            if (route.notes.isBlank()) "No notes yet. Tap edit to add your beta." else "“${route.notes}”"

        val tagGroup = view.findViewById<ChipGroup>(R.id.route_tags)
        tagGroup?.removeAllViews()
        route.tagList().forEach { tag ->
            tagGroup?.addView(Chip(requireContext()).apply { text = tag; isClickable = false; isCheckable = false })
        }
    }

    private fun bindAttempts(view: View, attempts: List<Attempt>) {
        val list = view.findViewById<LinearLayout>(R.id.attempt_list) ?: return
        list.removeAllViews()
        view.findViewById<TextView>(R.id.attempt_count)?.text =
            if (attempts.size == 1) "1 recorded attempt" else "${attempts.size} recorded attempts"
        view.findViewById<View>(R.id.attempt_empty)?.visibility = if (attempts.isEmpty()) View.VISIBLE else View.GONE

        val sent = attempts.any { it.sent }
        view.findViewById<TextView>(R.id.route_status)?.text = if (sent) "Sent ✓" else "Projecting"

        val inflater = LayoutInflater.from(requireContext())
        attempts.forEachIndexed { index, attempt ->
            val row = inflater.inflate(R.layout.item_attempt_row, list, false)
            val number = attempts.size - index // newest first
            row.findViewById<TextView>(R.id.attempt_title).text = String.format(Locale.US, "Attempt %02d", number)
            val meta = mutableListOf(RouteUi.shortDate(attempt.createdAt))
            if (attempt.durationMs > 0) meta.add(RouteUi.seconds(attempt.durationMs))
            meta.add(if (attempt.videoPath != null) "video" else "logged")
            row.findViewById<TextView>(R.id.attempt_meta).text = meta.joinToString(" · ")
            row.findViewById<TextView>(R.id.attempt_result).apply {
                text = if (attempt.sent) "Sent" else attempt.note.ifBlank { "Fell" }.take(18)
                setTextColor(ContextCompat.getColor(requireContext(), if (attempt.sent) R.color.green else R.color.coral))
            }
            row.setOnClickListener { openAttempt(attempt) }
            // Long-press: edit or delete this attempt
            row.setOnLongClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(String.format(Locale.US, "Attempt %02d", number))
                    .setItems(arrayOf("Edit", "Delete")) { _, which ->
                        if (which == 0) {
                            currentRoute?.let { showAttemptDialog(it, attempt) }
                        } else {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Delete this attempt?")
                                .setMessage("Its saved video and annotations are deleted too.")
                                .setPositiveButton("Delete") { _, _ -> viewModel.deleteAttempt(attempt) }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                    .show()
                true
            }
            list.addView(row)
        }
    }

    /** Quick log (existing == null) or edit an attempt's result and note. */
    private fun showAttemptDialog(route: Route, existing: Attempt?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_attempt, null)
        // The route is already known on this screen
        dialogView.findViewById<View>(R.id.label_route).visibility = View.GONE
        dialogView.findViewById<View>(R.id.spinner_route).visibility = View.GONE
        val sentButton = dialogView.findViewById<RadioButton>(R.id.radio_sent)
        val fellButton = dialogView.findViewById<RadioButton>(R.id.radio_fell)
        val noteInput = dialogView.findViewById<TextInputEditText>(R.id.input_attempt_note)
        if (existing != null) {
            sentButton.isChecked = existing.sent
            fellButton.isChecked = !existing.sent
            noteInput.setText(existing.note)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "Log attempt · ${route.name}" else "Edit attempt")
            .setView(dialogView)
            .setPositiveButton(if (existing == null) "Log it" else "Save") { _, _ ->
                val sent = sentButton.isChecked
                val note = noteInput.text?.toString()?.trim().orEmpty()
                if (existing == null) {
                    viewModel.logAttempt(route.id, sent, note)
                    Toast.makeText(requireContext(), "Attempt logged", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.updateAttempt(existing.copy(sent = sent, note = note))
                    Toast.makeText(requireContext(), "Attempt updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAttempt(attempt: Attempt) {
        val fragment = attempt.videoPath?.takeIf { File(it).exists() }?.let {
            AnalyzerFragment.forSavedAttempt(attempt.id, Uri.fromFile(File(it)))
        } ?: AnalyzerFragment.forSavedAttempt(attempt.id, null)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}
