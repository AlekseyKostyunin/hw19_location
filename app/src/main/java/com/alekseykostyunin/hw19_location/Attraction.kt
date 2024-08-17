package com.alekseykostyunin.hw19_location

import com.yandex.mapkit.geometry.Point

data class Attraction(
    val name: String,
    val description: String,
    val point: Point
)
