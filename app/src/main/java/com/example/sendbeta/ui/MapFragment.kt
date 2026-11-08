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
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.util.Calendar
import java.util.Locale

/**
 * One climbing gym shown on the map.
 * Hours use a 24h clock as decimals (7.5 = 7:30 AM, 23.0 = 11 PM).
 * [dayOverrides] replaces the weekday/weekend hours for specific days (keys = Calendar.MONDAY, etc.).
 * Source: torontoclimbs.ca (gym listings for the City of Toronto).
 */
data class Gym(
    val name: String,
    val chipLabel: String,
    val area: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val types: String,
    val dayPass: String,
    val weekdayOpen: Double,
    val weekdayClose: Double,
    val weekendOpen: Double,
    val weekendClose: Double,
    val dayOverrides: Map<Int, Pair<Double, Double>> = emptyMap()
) {
    val point: GeoPoint get() = GeoPoint(lat, lon)

    /** Opening hours (open, close) for a Calendar day-of-week. */
    fun hoursFor(day: Int): Pair<Double, Double> = dayOverrides[day]
        ?: if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) weekendOpen to weekendClose
        else weekdayOpen to weekdayClose
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

    // All open climbing gyms in the City of Toronto listed on torontoclimbs.ca
    private val gyms = listOf(
        Gym("The Rock Oasis", "Rock Oasis", "Leslieville",
            "204-388 Carlaw Ave, Toronto, ON", 43.6655, -79.3407,
            "Bouldering · Top rope · Lead · Auto-belay · Speed", "$28",
            9.0, 23.0, 9.0, 22.0),
        Gym("Basecamp Bloor West", "Basecamp Bloor", "Bloor West",
            "677 Bloor St W, Toronto, ON", 43.6644, -79.4176,
            "Bouldering · Top rope · Lead · Auto-belay", "$28",
            10.0, 23.0, 10.0, 22.0,
            mapOf(Calendar.TUESDAY to (7.0 to 23.0), Calendar.THURSDAY to (7.0 to 23.0))),
        Gym("Basecamp Queen West", "Basecamp Queen W", "Queen West",
            "186 Spadina Ave, Toronto, ON", 43.6497, -79.3967,
            "Bouldering", "$28",
            10.0, 23.0, 10.0, 22.0),
        Gym("Joe Rockhead's", "Joe Rockhead's", "Liberty Village",
            "29 Fraser Ave, Toronto, ON", 43.6380, -79.4256,
            "Bouldering · Top rope · Lead · Auto-belay", "$28",
            7.0, 23.0, 9.0, 22.0),
        Gym("Boulderz Junction", "Boulderz Junction", "The Junction",
            "1444 Dupont St Unit 16, Toronto, ON", 43.6668, -79.4470,
            "Bouldering · Auto-belay", "$28",
            9.0, 23.0, 9.0, 21.0),
        Gym("Boulderz Etobicoke", "Boulderz Etobicoke", "Etobicoke",
            "80 The East Mall Unit 9, Etobicoke, ON", 43.6222, -79.5603,
            "Bouldering · Top rope · Lead · Auto-belay", "$32",
            9.0, 23.0, 9.0, 21.0),
        Gym("Rock On Climbing", "Rock On", "Canary District",
            "474 Front St E, Toronto, ON", 43.6526, -79.3555,
            "Bouldering", "$24",
            16.0, 22.0, 10.0, 20.0),
        Gym("Ethos Climbing", "Ethos", "Waterfront",
            "128 Queens Quay E, Toronto, ON", 43.6449, -79.3678,
            "Bouldering", "$30",
            7.5, 23.0, 7.5, 22.0,
            mapOf(Calendar.MONDAY to (16.0 to 23.0), Calendar.TUESDAY to (16.0 to 23.0))),
        Gym("Toronto Climbing Academy", "Climbing Academy", "East York",
            "11 Curity Ave Unit 3, Toronto, ON", 43.7069, -79.3090,
            "Bouldering · Top rope · Lead · Auto-belay", "$31",
            10.0, 23.0, 10.0, 20.0),
        Gym("Hogtown Boulders", "Hogtown", "Don Mills",
            "238 Lesmill Rd Unit A, Toronto, ON", 43.7593, -79.3530,
            "Bouldering", "$30",
            10.0, 23.0, 10.0, 23.0),
        Gym("True North Climbing", "True North", "North York",
            "75 Carl Hall Rd Unit 14, North York, ON", 43.7488, -79.4703,
            "Bouldering · Top rope · Lead · Auto-belay", "$30",
            10.0, 22.0, 10.0, 22.0),
        Gym("Boulder Parc", "Boulder Parc", "Scarborough",
            "1415 Morningside Ave Unit 2, Scarborough, ON", 43.8033, -79.2101,
            "Bouldering", "$29",
            12.0, 23.0, 10.0, 20.0)
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
            // OSM tile usage policy: send a unique User-Agent with app name, version and a contact URL
            // (library defaults like "osmdroid" and generic ids like "com.example.*" are blocked)
            userAgentValue = "SendBeta/1.0 (+https://github.com/cozyyyaria/SendBeta)"
            osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            // Fresh cache folder so previously cached error tiles are never shown
            osmdroidTileCache = File(osmdroidBasePath, "tiles-osm-v3")
        }
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val map = view.findViewById<MapView>(R.id.map_view)
        mapView = map
        // Standard OpenStreetMap tiles (works now that the app sends a descriptive user agent)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true) // pinch to zoom, drag to pan
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER) // we use our own buttons
        map.minZoomLevel = 9.0 // keeps the view around the GTA
        map.maxZoomLevel = 19.0
        map.controller.setZoom(11.0)
        map.controller.setCenter(GeoPoint(43.7000, -79.4000))

        view.findViewById<TextView>(R.id.map_subtitle)?.text = "${gyms.size} climbing gyms across Toronto"

        addGymMarkers(map)
        setUpGymChips(view)

        view.findViewById<ImageButton>(R.id.btn_zoom_in)?.setOnClickListener { map.controller.zoomIn() }
        view.findViewById<ImageButton>(R.id.btn_zoom_out)?.setOnClickListener { map.controller.zoomOut() }
        view.findViewById<ImageButton>(R.id.btn_my_location)?.setOnClickListener { onMyLocationClicked() }
        view.findViewById<Button>(R.id.btn_directions)?.setOnClickListener { openDirections(gyms[selectedIndex]) }

        // Opened from a route ("View on map")? Zoom to that gym. Otherwise fit all gyms on screen.
        val gymLabel = arguments?.getString(ARG_GYM_LABEL)
        if (gymLabel != null) {
            selectGym(findGymIndex(gymLabel), animate = false)
        } else {
            selectGym(0, animate = false, moveCamera = false)
            map.post { showAllGyms() }
        }

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
    private fun showAllGyms() {
        val map = mapView ?: return
        val box = BoundingBox.fromGeoPoints(gyms.map { it.point })
        val padding = (40 * resources.displayMetrics.density).toInt()
        map.zoomToBoundingBox(box, false, padding)
    }

    private fun selectGym(index: Int, animate: Boolean = true, moveCamera: Boolean = true) {
        selectedIndex = index
        val gym = gyms[index]
        val v = view ?: return

        markers.forEachIndexed { i, marker ->
            marker.icon = markerIcon(i + 1, selected = i == index)
        }
        mapView?.let { map ->
            if (!moveCamera) { /* keep current view */ }
            else if (animate) map.controller.animateTo(gym.point, 14.0, 600L)
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
        v.findViewById<TextView>(R.id.gym_types)?.text = "${gym.types}  ·  ${gym.dayPass} day pass"
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
        val (open, close) = gym.hoursFor(now.get(Calendar.DAY_OF_WEEK))
        val time = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0

        val isOpen = time >= open && time < close
        val statusView = v.findViewById<TextView>(R.id.gym_hours)
        statusView?.text = if (isOpen) "Open" else "Closed"
        statusView?.setTextColor(Color.parseColor(if (isOpen) "#1A7F55" else "#D64545"))
        v.findViewById<TextView>(R.id.gym_hours_label)?.text = when {
            isOpen -> "until ${formatHour(close)}"
            time < open -> "opens ${formatHour(open)}"
            else -> "opens tomorrow"
        }
    }

    /** 7.5 -> "7:30 AM", 23.0 -> "11 PM" */
    private fun formatHour(value: Double): String {
        val h = value.toInt()
        val minutes = ((value - h) * 60).toInt()
        val hour12 = when {
            h == 0 || h == 24 -> 12
            h > 12 -> h - 12
            else -> h
        }
        val suffix = if (h in 12..23) "PM" else "AM"
        return if (minutes == 0) "$hour12 $suffix" else String.format(Locale.US, "%d:%02d %s", hour12, minutes, suffix)
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
