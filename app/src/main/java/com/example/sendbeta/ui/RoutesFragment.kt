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
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sendbeta.R
import com.example.sendbeta.data.Route
import com.example.sendbeta.data.SendBetaRepository
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class RoutesFragment : Fragment() {

    private val viewModel: RoutesViewModel by viewModels()
    private lateinit var adapter: RouteAdapter
    private var allRoutes: List<Route> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_routes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RouteAdapter(
            onClick = { route -> openRouteDetail(route) },
            onLongClick = { route -> showRouteOptions(route) }
        )
        view.findViewById<RecyclerView>(R.id.route_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RoutesFragment.adapter
        }

        // Live data from Room: the list refreshes automatically after add/edit/delete
        viewModel.routes.observe(viewLifecycleOwner) { routes ->
            allRoutes = routes
            applyFilters()
        }

        // Add Route button -> create dialog (saved to the database)
        view.findViewById<ImageButton>(R.id.btn_add_route)?.setOnClickListener {
            showRouteDialog(existing = null)
        }

        view.findViewById<EditText>(R.id.search_input)?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = applyFilters()
            override fun afterTextChanged(s: Editable?) {}
        })
        view.findViewById<ChipGroup>(R.id.filter_chips)?.setOnCheckedStateChangeListener { _, _ -> applyFilters() }
    }

    /** Search (name, gym, grade, tags) + style chip filter, applied to the routes from the database. */
    private fun applyFilters() {
        val v = view ?: return
        val query = v.findViewById<EditText>(R.id.search_input)?.text?.toString()?.trim()?.lowercase().orEmpty()
        val chipTag = when (v.findViewById<ChipGroup>(R.id.filter_chips)?.checkedChipId) {
            R.id.chip_overhang -> "Overhang"
            R.id.chip_crimps -> "Crimps"
            R.id.chip_slab -> "Slab"
            R.id.chip_dynamic -> "Dynamic"
            else -> null
        }

        val filtered = allRoutes.filter { route ->
            val matchesChip = chipTag == null || route.tagList().any { it.equals(chipTag, ignoreCase = true) }
            val haystack = "${route.name} ${route.gymLabel} ${route.grade} ${route.tags}".lowercase()
            matchesChip && (query.isEmpty() || haystack.contains(query))
        }
        adapter.submitList(filtered)

        v.findViewById<View>(R.id.empty_state)?.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        val gymCount = allRoutes.map { it.gymLabel }.distinct().size
        v.findViewById<TextView>(R.id.routes_subtitle)?.text =
            if (query.isEmpty() && chipTag == null) "${allRoutes.size} routes saved across $gymCount gyms"
            else "${filtered.size} routes found"
    }

    private fun showRouteOptions(route: Route) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(route.name)
            .setItems(arrayOf("Edit", "Delete")) { _, which ->
                when (which) {
                    0 -> showRouteDialog(existing = route)
                    1 -> RouteUi.confirmDelete(requireContext(), route.name) { viewModel.deleteRoute(route) }
                }
            }
            .show()
    }

    private fun showRouteDialog(existing: Route?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val gyms = SendBetaRepository.get(requireContext()).getGyms()
            RouteUi.showRouteDialog(
                context = requireContext(),
                gyms = gyms,
                existing = existing,
                onSave = { route -> if (existing == null) viewModel.addRoute(route) else viewModel.updateRoute(route) },
                onDelete = if (existing != null) ({ viewModel.deleteRoute(existing) }) else null
            )
        }
    }

    private fun openRouteDetail(route: Route) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, RouteDetailFragment.newInstance(route.id))
            .addToBackStack(null)
            .commit()
    }
}
