package com.alekseykostyunin.hw19_location

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.alekseykostyunin.hw19_location.databinding.ActivityMainBinding
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.user_location.UserLocationLayer
import com.yandex.runtime.image.ImageProvider
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var mapView: MapView
    private var saveLatitude = START_POINT.latitude
    private var saveLongitude = START_POINT.longitude
    private var saveZoom = 12.0f
    private var routeStartLocation = START_POINT
    private var followUserLocation = false
    private lateinit var userLocationLayer: UserLocationLayer
    private lateinit var pinsCollection: MapObjectCollection
    private var currentPopupWindow: PopupWindow? = null

    private val placeMarkTapListener = MapObjectTapListener { mapObject, _ ->
        val attraction = mapObject.userData as Attraction
        showCustomBalloon(attraction)
        true
    }

    private val launcher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
            if (map.values.all { it }) {
                turnOnGps()
                onMapReady()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.required_permissions),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapKitFactory.setApiKey("3916d5bc-7cfc-405d-874a-0023bd2955fe")
        MapKitFactory.initialize(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        mapView = binding.mapview
        setContentView(binding.root)
        mapView.mapWindow.map.move(START_POSITION)
        binding.location.setOnClickListener {
            followUserLocation = true
            if (checkOnGps()) cameraUserPosition() else turnOnGps()
        }
        binding.buttonZoomPlus.setOnClickListener { changeZoomButton(+ZOOM_STEP) }
        binding.buttonZoomMinus.setOnClickListener { changeZoomButton(-ZOOM_STEP) }
        if (savedInstanceState != null) {
            saveLatitude = savedInstanceState.getDouble("latitude")
            saveLongitude = savedInstanceState.getDouble("longitude")
            saveZoom = savedInstanceState.getFloat("zoom")
            routeStartLocation = Point(saveLatitude, saveLongitude)
            cameraSavePosition(saveZoom)
        }
    }

    private fun cameraUserPosition() {
        if (userLocationLayer.cameraPosition() != null && followUserLocation) {
            routeStartLocation = userLocationLayer.cameraPosition()!!.target
            binding.mapview.mapWindow.map.move(
                CameraPosition(routeStartLocation, 16f, 0f, 0f),
                Animation(Animation.Type.SMOOTH, 1f),
                null
            )
        }
    }

    private fun cameraSavePosition(saveZoom: Float) {
        mapView.mapWindow.map.move(
            CameraPosition(
                Point(saveLatitude, saveLongitude),
                saveZoom,
                0f,
                0f
            )
        )
    }

    private fun checkPermission() {
        val isAllGranted = REQUEST_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
        if (isAllGranted) {
            turnOnGps()
            if (checkOnGps()) {
                onMapReady()
            }
        } else {
            launcher.launch(REQUEST_PERMISSIONS)
        }
    }

    private fun turnOnGps() {
        if (!checkOnGps()) {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.ask_turn_GPS_network))
                .setPositiveButton(
                    R.string.open_location_settings
                ) { _, _ ->
                    this.startActivity(
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    )
                }
                .setNegativeButton(R.string.Cancel) { _, _ ->
                    supportFragmentManager.popBackStack()
                }
                .show()
        }
    }

    private fun checkOnGps(): Boolean {
        val lm = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var gpsEnabled = false
        var networkEnabled = false
        try {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
        }

        try {
            networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
        }
        return gpsEnabled && networkEnabled
    }


    private fun onMapReady() {
        val mapKit = MapKitFactory.getInstance()
        try {
            userLocationLayer = mapKit.createUserLocationLayer(binding.mapview.mapWindow)
            userLocationLayer.isVisible = true
        } catch (_: RuntimeException) {
        }

        mapView.mapWindow.map.addInputListener(object : InputListener {
            override fun onMapTap(map: Map, point: Point) {
                currentPopupWindow?.dismiss()
            }
            override fun onMapLongTap(map: Map, point: Point) {}
        })

        viewModel.listAttractions.onEach { listAttractions ->
            val imageProvider =
                ImageProvider.fromResource(this, R.drawable.mark)

            pinsCollection = mapView.mapWindow.map.mapObjects.addCollection()
            listAttractions.forEach { attraction ->
                pinsCollection.addPlacemark().apply {
                    geometry = attraction.point
                    setIcon(imageProvider)
                    userData = attraction
                    addTapListener(placeMarkTapListener)
                }
            }
        }.launchIn(lifecycleScope)
    }

    private fun showCustomBalloon(attraction: Attraction) {
        currentPopupWindow?.dismiss()
        val balloonView = LayoutInflater.from(this).inflate(R.layout.balloon_layout, null)
        val title = balloonView.findViewById<TextView>(R.id.balloon_title)
        val description = balloonView.findViewById<TextView>(R.id.balloon_description)
        title.text = attraction.name
        description.text = attraction.description
        val popupWindow = PopupWindow(
            balloonView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        popupWindow.showAtLocation(binding.root, Gravity.CENTER, 0, 0)
        currentPopupWindow = popupWindow
        val closeButton = balloonView.findViewById<Button>(R.id.close_button)
        closeButton.setOnClickListener {
            popupWindow.dismiss()
        }
    }

    private fun changeZoomButton(value: Float) {
        with(mapView.mapWindow.map.cameraPosition) {
            mapView.mapWindow.map.move(
                CameraPosition(target, zoom + value, azimuth, tilt),
                SMOOTH_ANIMATION,
                null,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        checkPermission()
        mapView.onStart()
        MapKitFactory.getInstance().onStart()
    }

    override fun onStop() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putDouble("latitude", saveLatitude)
        outState.putDouble("longitude", saveLongitude)
        outState.putFloat("zoom", saveZoom)
    }

    companion object {
        private val START_POINT = Point(51.740516, 58.791368)
        private val START_POSITION = CameraPosition(START_POINT, 13.0f, 0f, 0f)
        private const val ZOOM_STEP = 1f
        private val SMOOTH_ANIMATION = Animation(Animation.Type.SMOOTH, 0.5f)
        private val REQUEST_PERMISSIONS: Array<String> = buildList {
            add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }.toTypedArray()
    }
}