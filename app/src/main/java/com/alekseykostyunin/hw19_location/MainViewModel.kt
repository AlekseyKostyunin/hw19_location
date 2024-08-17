package com.alekseykostyunin.hw19_location

import androidx.lifecycle.ViewModel
import com.yandex.mapkit.geometry.Point
import kotlinx.coroutines.flow.asFlow

class MainViewModel : ViewModel() {

    private val pearl = Attraction(
        "Жемчужина",
        "База, дом отдыха",
        Point(51.743833, 58.788998)
    )

    private val dkSovremennik = Attraction(
        "Дом культуры Современник",
        "Дом культуры, кинотеатр",
        Point(51.738131, 58.791234)
    )

    private val dolpin = Attraction(
        "Дельфин",
        "Спортивный комплекс, бассейн",
        Point(51.737065, 58.791786)
    )

    private val listAttraction = mutableListOf<Attraction>(
        pearl,
        dkSovremennik,
        dolpin
    )

    val listAttractions = listOf(listAttraction).asFlow()
}