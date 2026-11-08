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
        val emptyState = view.findViewById<View>(R.id.empty_state)
        val subtitle = view.findViewById<TextView>(R.id.routes_subtitle)
        val searchInput = view.findViewById<EditText>(R.id.search_input)
        val chipGroup = view.findViewById<ChipGroup>(R.id.filter_chips)

        // Route click listeners
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

        // Add Route button -> CaptureFragment
        view.findViewById<ImageButton>(R.id.btn_add_route)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CaptureFragment())
                .addToBackStack(null)
                .commit()
        }

        // Filter and Search logic matching React prototype
        fun updateVisibility() {
            val query = searchInput?.text?.toString()?.trim()?.lowercase() ?: ""
            val checkedChipId = chipGroup?.checkedChipId ?: R.id.chip_all

            // Tag/filter checks
            val matchChip1 = checkedChipId == R.id.chip_all
            val matchChip2 = checkedChipId == R.id.chip_all || checkedChipId == R.id.chip_overhang || checkedChipId == R.id.chip_dynamic
            val matchChip3 = checkedChipId == R.id.chip_all
            val matchChip4 = checkedChipId == R.id.chip_all || checkedChipId == R.id.chip_slab || checkedChipId == R.id.chip_crimps

            // Query checks
            val matchQuery1 = query.isEmpty() || "the pink arête rock oasis toronto arête balance".contains(query)
            val matchQuery2 = query.isEmpty() || "the dyno problem basecamp queen west dyno overhang".contains(query)
            val matchQuery3 = query.isEmpty() || "sloper traverse joe rockhead liberty slopers roof".contains(query)
            val matchQuery4 = query.isEmpty() || "quiet feet boulderz etobicoke slab crimps".contains(query)

            val show1 = matchChip1 && matchQuery1
            val show2 = matchChip2 && matchQuery2
            val show3 = matchChip3 && matchQuery3
            val show4 = matchChip4 && matchQuery4

            card1?.visibility = if (show1) View.VISIBLE else View.GONE
            card2?.visibility = if (show2) View.VISIBLE else View.GONE
            card3?.visibility = if (show3) View.VISIBLE else View.GONE
            card4?.visibility = if (show4) View.VISIBLE else View.GONE

            val count = (if (show1) 1 else 0) + (if (show2) 1 else 0) + (if (show3) 1 else 0) + (if (show4) 1 else 0)
            emptyState?.visibility = if (count == 0) View.VISIBLE else View.GONE

            subtitle?.text = if (query.isEmpty() && checkedChipId == R.id.chip_all) {
                "4 routes saved across 3 Toronto gyms"
            } else {
                "$count routes found"
            }
        }

        searchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateVisibility()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        chipGroup?.setOnCheckedStateChangeListener { _, _ ->
            updateVisibility()
        }
    }

    private fun openRouteDetail(name: String, gym: String, grade: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, RouteDetailFragment.newInstance(name, gym, grade))
            .addToBackStack(null)
            .commit()
    }
}
