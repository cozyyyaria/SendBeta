package com.example.sendbeta.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.sendbeta.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class CaptureFragment : Fragment() {

    companion object {
        private const val ARG_ROUTE_ID = "route_id"

        /** Open Capture for a specific route (from "Log an attempt"). */
        fun newInstance(routeId: Long): CaptureFragment = CaptureFragment().apply {
            arguments = Bundle().apply { putLong(ARG_ROUTE_ID, routeId) }
        }
    }

    private val routeId: Long? get() = arguments?.getLong(ARG_ROUTE_ID)?.takeIf { it > 0 }

    // Import: system file picker filtered to videos (Downloads, Photos, Drive, etc.)
    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) openAnalyzer(uri, "Imported video")
    }

    // Record: hands off to the device's camera app (the emulator has a virtual camera)
    private val recordVideo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (uri != null) {
            openAnalyzer(uri, "Recorded attempt")
        } else if (result.resultCode != android.app.Activity.RESULT_CANCELED) {
            toast("No video was returned by the camera")
        }
    }

    // Camera permission is needed because the app declares CAMERA in the manifest
    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else toast("Camera permission denied. You can still import a video.")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_capture, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Back button: go back if there's a previous screen, otherwise return to the Home tab
        view.findViewById<ImageButton>(R.id.btn_back)?.setOnClickListener {
            if (parentFragmentManager.backStackEntryCount > 0) {
                parentFragmentManager.popBackStack()
            } else {
                activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                    ?.selectedItemId = R.id.nav_home
            }
        }

        // Record button -> camera app
        view.findViewById<View>(R.id.btn_record)?.setOnClickListener {
            val ctx = requireContext()
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                requestCamera.launch(Manifest.permission.CAMERA)
            }
        }

        // Flip camera: handled inside the camera app once recording starts
        view.findViewById<View>(R.id.btn_flip)?.setOnClickListener {
            toast("Switch front/back camera inside the camera app")
        }

        // Gallery button -> pick an existing climbing video
        view.findViewById<View>(R.id.btn_gallery)?.setOnClickListener {
            pickVideo.launch("video/*")
        }
    }

    private fun launchCamera() {
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, 60) // keep attempts short
        }
        try {
            recordVideo.launch(intent)
        } catch (_: ActivityNotFoundException) {
            toast("No camera app found. Import a video instead.")
        }
    }

    private fun openAnalyzer(uri: Uri, source: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AnalyzerFragment.newInstance(uri, source, routeId))
            .addToBackStack(null)
            .commit()
    }

    private fun toast(message: String) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
    }
}
