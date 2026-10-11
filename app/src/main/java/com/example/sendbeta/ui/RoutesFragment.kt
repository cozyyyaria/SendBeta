package com.example.sendbeta.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.sendbeta.R
import com.google.android.material.chip.ChipGroup

class RoutesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_routes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val card1 = view.findViewById<View>(R.id.route_card_1)
        val card2 = view.findViewById<View>(R.id.route_card_2)
        val card3 = view.findViewById<View>(R.id.route_card_3)
        val card4 = view.findViewById<View>(R.id.route_card_4)

        val div1 = view.findViewById<View>(R.id.divider_1)
        val div2 = view.findViewById<View>(R.id.divider_2)
        val div3 = view.findViewById<View>(R.id.divider_3)

        // Route clicks
        card1?.setOnClickListener {
            openRouteDetail("The Pink Arête", "Rock Oasis · Toronto", "V5")
        }

        card2?.setOnClickListener {
            openRouteDetail("The Dyno Problem", "Basecamp · Queen West", "V4")
        }

        card3?.setOnClickListener {
            openRouteDetail("Sloper Traverse", "Joe Rockhead's · Liberty", "V6")
        }

        card4?.setOnClickListener {
            openRouteDetail("Quiet Feet", "Boulderz · Etobicoke", "V3")
        }

        // Add route button
        view.findViewById<ImageButton>(R.id.btn_add_route)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CaptureFragment())
                .addToBackStack(null)
                .commit()
        }

        // Search filtering
        view.findViewById<EditText>(R.id.search_input)?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim()?.lowercase() ?: ""
                card1?.visibility = if (query.isEmpty() || "pink arête rock oasis".contains(query)) View.VISIBLE else View.GONE
                card2?.visibility = if (query.isEmpty() || "dyno problem basecamp".contains(query)) View.VISIBLE else View.GONE
                card3?.visibility = if (query.isEmpty() || "sloper traverse joe rockhead".contains(query)) View.VISIBLE else View.GONE
                card4?.visibility = if (query.isEmpty() || "quiet feet boulderz".contains(query)) View.VISIBLE else View.GONE

                div1?.visibility = if (card1?.visibility == View.VISIBLE && (card2?.visibility == View.VISIBLE || card3?.visibility == View.VISIBLE || card4?.visibility == View.VISIBLE)) View.VISIBLE else View.GONE
                div2?.visibility = if (card2?.visibility == View.VISIBLE && (card3?.visibility == View.VISIBLE || card4?.visibility == View.VISIBLE)) View.VISIBLE else View.GONE
                div3?.visibility = if (card3?.visibility == View.VISIBLE && card4?.visibility == View.VISIBLE) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Filter chips
        val chipGroup = view.findViewById<ChipGroup>(R.id.filter_chips)
        chipGroup?.setOnCheckedStateChangeListener { _, checkedIds ->
            val firstId = checkedIds.firstOrNull() ?: R.id.chip_all
            when (firstId) {
                R.id.chip_overhang -> {
                    card1?.visibility = View.GONE
                    card2?.visibility = View.VISIBLE
                    card3?.visibility = View.GONE
                    card4?.visibility = View.GONE
                    div1?.visibility = View.GONE
                    div2?.visibility = View.GONE
                    div3?.visibility = View.GONE
                }
                R.id.chip_crimps -> {
                    card1?.visibility = View.GONE
                    card2?.visibility = View.GONE
                    card3?.visibility = View.GONE
                    card4?.visibility = View.VISIBLE
                    div1?.visibility = View.GONE
                    div2?.visibility = View.GONE
                    div3?.visibility = View.GONE
                }
                R.id.chip_slab -> {
                    card1?.visibility = View.GONE
                    card2?.visibility = View.GONE
                    card3?.visibility = View.GONE
                    card4?.visibility = View.VISIBLE
                    div1?.visibility = View.GONE
                    div2?.visibility = View.GONE
                    div3?.visibility = View.GONE
                }
                R.id.chip_dynamic -> {
                    card1?.visibility = View.GONE
                    card2?.visibility = View.VISIBLE
                    card3?.visibility = View.GONE
                    card4?.visibility = View.GONE
                    div1?.visibility = View.GONE
                    div2?.visibility = View.GONE
                    div3?.visibility = View.GONE
                }
                else -> { // All
                    card1?.visibility = View.VISIBLE
                    card2?.visibility = View.VISIBLE
                    card3?.visibility = View.VISIBLE
                    card4?.visibility = View.VISIBLE
                    div1?.visibility = View.VISIBLE
                    div2?.visibility = View.VISIBLE
                    div3?.visibility = View.VISIBLE
                }
            }
        }

        val subtitleView = view.findViewById<TextView>(R.id.routes_subtitle)
        subtitleView?.text = getString(R.string.routes_subtitle, 4)
    }

    private fun openRouteDetail(name: String, gym: String, grade: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, RouteDetailFragment.newInstance(name, gym, grade))
            .addToBackStack(null)
            .commit()
    }
}
