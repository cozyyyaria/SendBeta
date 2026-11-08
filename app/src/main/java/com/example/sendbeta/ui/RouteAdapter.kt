package com.example.sendbeta.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sendbeta.R
import com.example.sendbeta.data.Route

/** RecyclerView adapter for the Routes list (uses item_route.xml). */
class RouteAdapter(
    private val onClick: (Route) -> Unit,
    private val onLongClick: (Route) -> Unit
) : ListAdapter<Route, RouteAdapter.RouteViewHolder>(DIFF) {

    class RouteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val swatch: FrameLayout = view.findViewById(R.id.route_swatch_frame)
        val swatchIcon: ImageView = view.findViewById(R.id.route_swatch_icon)
        val name: TextView = view.findViewById(R.id.route_name)
        val location: TextView = view.findViewById(R.id.route_location)
        val tag1: TextView = view.findViewById(R.id.tag_1)
        val tag2: TextView = view.findViewById(R.id.tag_2)
        val rating: TextView = view.findViewById(R.id.route_rating)
        val grade: TextView = view.findViewById(R.id.route_grade)
        val options: View = view.findViewById(R.id.btn_route_options)
    }

    /** Route shown at a position (used by swipe-to-delete). */
    fun routeAt(position: Int): Route = getItem(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder =
        RouteViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_route, parent, false))

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = getItem(position)
        val ctx = holder.itemView.context
        holder.name.text = route.name
        holder.location.text = route.gymLabel
        holder.grade.text = route.grade
        holder.grade.setBackgroundResource(RouteUi.gradeBadgeRes(route.color))
        holder.swatch.setBackgroundResource(RouteUi.swatchRes(route.color))
        holder.swatchIcon.setColorFilter(ContextCompat.getColor(ctx, RouteUi.tintRes(route.color)))
        holder.rating.text = RouteUi.stars(route.rating)

        val tags = route.tagList()
        holder.tag1.text = tags.getOrNull(0)
        holder.tag1.visibility = if (tags.isNotEmpty()) View.VISIBLE else View.GONE
        holder.tag2.text = tags.getOrNull(1)
        holder.tag2.visibility = if (tags.size > 1) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onClick(route) }
        holder.itemView.setOnLongClickListener { onLongClick(route); true }
        holder.options.setOnClickListener { onLongClick(route) } // visible ✎ button = same Edit/Delete menu
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Route>() {
            override fun areItemsTheSame(a: Route, b: Route) = a.id == b.id
            override fun areContentsTheSame(a: Route, b: Route) = a == b
        }
    }
}
