package com.example.sendbeta.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.RatingBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.sendbeta.R
import com.example.sendbeta.data.Gym
import com.example.sendbeta.data.Route
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shared helpers for showing routes (colours, stars, dates) and the add/edit route dialog. */
object RouteUi {

    val STYLE_TAGS = listOf("Overhang", "Crimps", "Slab", "Dynamic", "Balance", "Slopers", "Arête")
    private val GRADES = (0..10).map { "V$it" }

    fun swatchRes(color: String) = when (color) {
        "coral" -> R.drawable.swatch_coral
        "lime" -> R.drawable.swatch_lime
        "blue" -> R.drawable.swatch_blue
        else -> R.drawable.swatch_violet
    }

    fun tintRes(color: String) = when (color) {
        "coral" -> R.color.coral
        "lime" -> R.color.lime_dark
        "blue" -> R.color.blue
        else -> R.color.violet
    }

    fun gradeBadgeRes(color: String) = when (color) {
        "coral" -> R.drawable.grade_badge_coral
        "lime" -> R.drawable.grade_badge_lime
        "blue" -> R.drawable.grade_badge_blue
        else -> R.drawable.grade_badge_violet
    }

    fun stars(rating: Int): String {
        val r = rating.coerceIn(0, 5)
        return "★".repeat(r) + "☆".repeat(5 - r) + " $r.0"
    }

    fun shortDate(millis: Long): String = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))

    fun seconds(ms: Long): String = "${ms / 1000}s"

    /**
     * Add (existing == null) or edit a route. [onDelete] adds a Delete button when editing.
     */
    fun showRouteDialog(
        context: Context,
        gyms: List<Gym>,
        existing: Route?,
        onSave: (Route) -> Unit,
        onDelete: (() -> Unit)? = null
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_route, null)
        val nameInput = view.findViewById<TextInputEditText>(R.id.input_route_name)
        val gymSpinner = view.findViewById<Spinner>(R.id.spinner_gym)
        val gradeSpinner = view.findViewById<Spinner>(R.id.spinner_grade)
        val tagGroup = view.findViewById<ChipGroup>(R.id.chips_tags)
        val ratingBar = view.findViewById<RatingBar>(R.id.rating_bar)
        val notesInput = view.findViewById<TextInputEditText>(R.id.input_notes)

        // Gyms come from the database
        val gymLabels = gyms.map { it.label() }.toMutableList()
        if (existing != null && existing.gymLabel !in gymLabels) gymLabels.add(0, existing.gymLabel)
        gymSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, gymLabels)
        gradeSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, GRADES)

        val selectedTags = existing?.tagList().orEmpty()
        STYLE_TAGS.forEach { tag ->
            tagGroup.addView(Chip(context).apply {
                text = tag
                isCheckable = true
                isChecked = tag in selectedTags
            })
        }

        if (existing != null) {
            nameInput.setText(existing.name)
            gymSpinner.setSelection(gymLabels.indexOf(existing.gymLabel).coerceAtLeast(0))
            gradeSpinner.setSelection(GRADES.indexOf(existing.grade).coerceAtLeast(0))
            ratingBar.rating = existing.rating.toFloat()
            notesInput.setText(existing.notes)
        } else {
            gradeSpinner.setSelection(3)
            ratingBar.rating = 3f
        }

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(if (existing == null) "New route" else "Edit route")
            .setView(view)
            .setPositiveButton(if (existing == null) "Add route" else "Save", null)
            .setNegativeButton("Cancel", null)
        if (existing != null && onDelete != null) {
            builder.setNeutralButton("Delete") { _, _ -> confirmDelete(context, existing.name, onDelete) }
        }
        val dialog = builder.create()
        dialog.show()

        // Validate before closing
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = nameInput.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                nameInput.error = "Give your route a name"
                return@setOnClickListener
            }
            val grade = gradeSpinner.selectedItem as String
            val tags = (0 until tagGroup.childCount)
                .map { tagGroup.getChildAt(it) as Chip }
                .filter { it.isChecked }
                .joinToString(",") { it.text }
            val route = (existing ?: Route(name = "", gymLabel = "", grade = "", tags = "", color = "", rating = 0, notes = ""))
                .copy(
                    name = name,
                    gymLabel = gymSpinner.selectedItem as String,
                    grade = grade,
                    tags = tags,
                    color = Route.colorForGrade(grade),
                    rating = ratingBar.rating.toInt().coerceIn(1, 5),
                    notes = notesInput.text?.toString()?.trim().orEmpty()
                )
            onSave(route)
            Toast.makeText(context, if (existing == null) "Route added" else "Route updated", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }

    fun confirmDelete(context: Context, name: String, onDelete: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Delete \"$name\"?")
            .setMessage("This also deletes all of its attempts and saved videos.")
            .setPositiveButton("Delete") { _, _ -> onDelete() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
