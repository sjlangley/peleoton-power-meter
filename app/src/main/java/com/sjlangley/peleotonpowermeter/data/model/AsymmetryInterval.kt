package com.sjlangley.peleotonpowermeter.data.model

data class AsymmetryInterval(
    val startLabel: String,
    val endLabel: String,
    val leftPercent: Int,
    val rightPercent: Int,
    val supported: Boolean,
)
