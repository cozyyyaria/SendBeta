package com.example.sendbeta.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.sendbeta.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.util.Calendar
import java.util.Locale

/** One climbing gym shown on the map. Hours are 24h clock (e.g. 23 = 11 PM). */
data class Gym(
    val name: String,
    val chipLabel: String,
    val area: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val weekdayOpen: Int,
    val weekdayClose: Int,
    val weekendOpen: Int,
    val weekendClose: Int
) {
    val point: GeoPoint get() = GeoPoint(lat, lon)
}

class MapFragment : Fragment() {

    companion object {
        private const val ARG_GYM_LABEL = "gym_label"

        // Used for distances when we don't have the user's real location
        private val DOWNTOWN_TORONTO = GeoPoint(43.6453, -79.3806) // Union Station
        private const val TORONTO_RADIUS_METERS = 100_000.0

        /** Open the map with a gym preselected, e.g. "Basecamp · Queen West" from a route. */
        fun newInstance(gymLabel: String): MapFragment = MapFragment().apply {
            arguments = Bundle().apply { putString(ARG_GYM_LABEL, gymLabel) }
        }
    }

    private val gyms = listOf(
        Gym("The Rock Oasis", "Rock Oasis", "Leslieville",
            "388 Carlaw Ave, Toronto, ON", 43.6669, -79.3409, 9, 23, 9, 22),
        Gym("Basecamp Bloor West", "Basecamp Bloor", "Bloor West",
            "677 Bloor St W, Toronto, ON", 43.6643, -79.4166, 10, 23, 10, 22),
        Gym("Basecamp Queen West", "Basecamp Queen W", "Queen West",
            "186 Spadina Ave, Toronto, ON", 43.6491, -79.3965, 10, 23, 10, 22),
        Gym("Joe Rockhead's", "Joe Rockhead's", "Liberty Village",
            "29 Fraser Ave, Toronto, ON", 43.6381, -79.4253, 9, 22, 9, 22),
        Gym("Boulderz Etobicoke", "Boulderz", "Etobicoke",
            "80 The East Mall, Etobicoke, ON", 43.6230, -79.5605, 12, 23, 9, 21)
    )

    private var mapView: MapView? = null
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private val markers = mutableListOf<Marker>()
    private val chipIds = mutableListOf<Int>()
    private var selectedIndex = 0

    // Where distances are measured from (real location if we have it, otherwise downtown)
    private var userPoint: GeoPoint = DOWNTOWN_TORONTO
    private var usingRealLocation = false

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) {
                enableMyLocation(centerOnFix = true)
            } else {
                toast("Location permission denied. Showing distances from downtown.")
                mapView?.controller?.animateTo(DOWNTOWN_TORONTO)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // osmdroid setup: must happen before the MapView is inflated
        val ctx = requireContext().applicationContext
        Configuration.getInstance().apply {
            load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = ctx.packageName // OpenStreetMap requires an identifying user agent
            osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val map = view.findViewById<MapView>(R.id.map_view)
        mapView = map
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true) // pinch to zoom, drag to pan
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER) // we use our own buttons
        map.minZoomLevel = 9.0
        map.maxZoomLevel = 19.0
        map.controller.setZoom(12.0)
        map.controller.setCenter(GeoPoint(43.6532, -79.4300))

        view.findViewById<TextView>(R.id.map_subtitle)?.text = "${gyms.size} gyms around Toronto"

        addGymMarkers(map)
        setUpGymChips(view)

        view.findViewById<ImageButton>(R.id.btn_zoom_in)?.setOnClickListener { map.controller.zoomIn() }
        view.findViewById<ImageButton>(R.id.btn_zoom_out)?.setOnClickListener { map.controller.zoomOut() }
        view.findViewById<ImageButton>(R.id.btn_my_location)?.setOnClickListener { onMyLocationClicked() }
        view.findViewById<Button>(R.id.btn_directions)?.setOnClickListener { openDirections(gyms[selectedIndex]) }

        // Preselect a gym if we were opened from a route ("View on map")
        val startIndex = arguments?.getString(ARG_GYM_LABEL)?.let { findGymIndex(it) } ?: 0
        selectGym(startIndex, animate = false)

        // If permission was already granted earlier, show the blue dot quietly
        if (hasLocationPermission()) enableMyLocation(centerOnFix = false)
    }

    // ---------- Markers & chips ----------

    private fun addGymMarkers(map: MapView) {
        gyms.forEachIndexed { index, gym ->
            val marker = Marker(map).apply {
                position = gym.point
                title = gym.name
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = markerIcon(index + 1, selected = false)
                setOnMarkerClickListener { _, _ ->
                    selectGym(index)
                    true // true = we handled it, don't show the default info bubble
                }
            }
            markers.add(marker)
            map.overlays.add(marker)
        }
    }

    private fun setUpGymChips(view: View) {
        val chipGroup = view.findViewById<ChipGroup>(R.id.gym_chips) ?: return
        gyms.forEach { gym ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = gym.chipLabel
                isCheckable = true
            }
            chipIds.add(chip.id)
            chipGroup.addView(chip)
        }
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val index = chipIds.indexOf(checkedId)
            if (index >= 0 && index != selectedIndex) selectGym(index)
        }
    }

    /** Highlights the gym on the map, in the chips, and fills in the info card. */
    private fun selectGym(index: Int, animate: Boolean = true) {
        selectedIndex = index
        val gym = gyms[index]
        val v = view ?: return

        markers.forEachIndexed { i, marker ->
            marker.icon = markerIcon(i + 1, selected = i == index)
        }
        mapView?.let { map ->
            if (animate) map.controller.animateTo(gym.point, 14.0, 600L)
            else {
                map.controller.setZoom(13.0)
                map.controller.setCenter(gym.point)
            }
            map.invalidate()
        }

        // Keep the chip row in sync and scrolled to the selected gym
        val chipGroup = v.findViewById<ChipGroup>(R.id.gym_chips)
        chipIds.getOrNull(index)?.let { id ->
            chipGroup?.check(id)
            val chip = chipGroup?.findViewById<View>(id)
            val scroll = v.findViewById<HorizontalScrollView>(R.id.gym_chips_scroll)
            scroll?.post { chip?.let { scroll.smoothScrollTo(it.left, 0) } }
        }

        v.findViewById<TextView>(R.id.gym_name)?.text = gym.name
        v.findViewById<TextView>(R.id.gym_address)?.text = gym.address
        updateDistance(gym)
        updateHours(gym)
    }

    private fun updateDistance(gym: Gym) {
        val v = view ?: return
        val km = userPoint.distanceToAsDouble(gym.point) / 1000.0
        v.findViewById<TextView>(R.id.gym_distance)?.text = String.format(Locale.US, "%.1f km", km)
        v.findViewById<TextView>(R.id.gym_distance_label)?.text =
            if (usingRealLocation) "from you" else "from downtown"
    }

    private fun updateHours(gym: Gym) {
        val v = view ?: return
        val now = Calendar.getInstance()
        val day = now.get(Calendar.DAY_OF_WEEK)
        val isWeekend = day == Calendar.SATURDAY || day == Calendar.SUNDAY
        val open = if (isWeekend) gym.weekendOpen else gym.weekdayOpen
        val close = if (isWeekend) gym.weekendClose else gym.weekdayClose
        val hour = now.get(Calendar.HOUR_OF_DAY)

        val isOpen = hour in open until close
        val statusView = v.findViewById<TextView>(R.id.gym_hours)
        statusView?.text = if (isOpen) "Open" else "Closed"
        statusView?.setTextColor(Color.parseColor(if (isOpen) "#1A7F55" else "#D64545"))
        v.findViewById<TextView>(R.id.gym_hours_label)?.text = when {
            isOpen -> "until ${formatHour(close)}"
            hour < open -> "opens ${formatHour(open)}"
            else -> "opens tomorrow"
        }
    }

    private fun formatHour(h: Int): String = when {
        h == 0 || h == 24 -> "12 AM"
        h < 12 -> "$h AM"
        h == 12 -> "12 PM"
        else -> "${h - 12} PM"
    }

    /** Matches a route's gym label like "Joe Rockhead's · Liberty" to a gym in the list. */
    private fun findGymIndex(label: String): Int {
        val parts = label.split("·").map { it.trim() }
        val brand = parts.getOrNull(0).orEmpty()
        val area = parts.getOrNull(1).orEmpty()
        val sameBrand = gyms.indices.filter { gyms[it].name.contains(brand, ignoreCase = true) }
        return sameBrand.firstOrNull { gyms[it].area.contains(area, ignoreCase = true) }
            ?: sameBrand.firstOrNull()
            ?: 0
    }

    /** Draws a round numbered pin: lime + bigger when selected, dark otherwise. */
    private fun markerIcon(number: Int, selected: Boolean): Drawable {
        val density = resources.displayMetrics.density
        val sizePx = ((if (selected) 40 else 32) * density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = sizePx / 2f
        val stroke = 3 * density

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(if (selected) "#D9FF52" else "#18201D")
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selected) Color.parseColor("#18201D") else Color.WHITE
            textSize = (if (selected) 15 else 13) * density
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawCircle(radius, radius, radius - stroke, fill)
        canvas.drawCircle(radius, radius, radius - stroke, border)
        val textY = radius - (text.descent() + text.ascent()) / 2
        canvas.drawText(number.toString(), radius, textY, text)
        return BitmapDrawable(resources, bitmap)
    }

    // ---------- Location ----------

    private fun hasLocationPermission(): Boolean {
        val ctx = context ?: return false
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun onMyLocationClicked() {
        if (hasLocationPermission()) {
            enableMyLocation(centerOnFix = true)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun enableMyLocation(centerOnFix: Boolean) {
        val map = mapView ?: return
        val overlay = myLocationOverlay ?: MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), map).also {
            it.enableMyLocation()
            map.overlays.add(it)
            myLocationOverlay = it
        }

        val current = overlay.myLocation
        if (current != null) {
            onLocationFix(current, centerOnFix)
        } else {
            if (centerOnFix) toast("Finding your location…")
            overlay.runOnFirstFix {
                val fix = overlay.myLocation
                activity?.runOnUiThread { if (fix != null) onLocationFix(fix, centerOnFix) }
            }
        }
    }

    private fun onLocationFix(location: GeoPoint, centerOnFix: Boolean) {
        if (view == null) return
        if (location.distanceToAsDouble(DOWNTOWN_TORONTO) > TORONTO_RADIUS_METERS) {
            // e.g. the emulator's default location is in California
            usingRealLocation = false
            userPoint = DOWNTOWN_TORONTO
            if (centerOnFix) {
                toast("You're outside Toronto. Showing distances from downtown.")
                mapView?.controller?.animateTo(DOWNTOWN_TORONTO, 13.0, 600L)
            }
        } else {
            usingRealLocation = true
            userPoint = location
            if (centerOnFix) mapView?.controller?.animateTo(location, 14.0, 600L)
        }
        updateDistance(gyms[selectedIndex])
    }

    // ---------- Directions ----------

    private fun openDirections(gym: Gym) {
        val query = Uri.encode("${gym.name}, ${gym.address}")
        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${gym.lat},${gym.lon}?q=$query"))
        try {
            startActivity(geoIntent)
        } catch (_: Exception) {
            try {
                val web = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$query")
                startActivity(Intent(Intent.ACTION_VIEW, web))
            } catch (_: Exception) {
                toast("Could not open directions")
            }
        }
    }

    private fun toast(message: String) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
    }

    // ---------- Lifecycle (osmdroid needs these) ----------

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        myLocationOverlay?.enableMyLocation()
        if (view != null) updateHours(gyms[selectedIndex])
    }

    override fun onPause() {
        super.onPause()
        myLocationOverlay?.disableMyLocation()
        mapView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        myLocationOverlay?.disableMyLocation()
        myLocationOverlay = null
        mapView?.onDetach()
        mapView = null
        markers.clear()
        chipIds.clear()
    }
}
