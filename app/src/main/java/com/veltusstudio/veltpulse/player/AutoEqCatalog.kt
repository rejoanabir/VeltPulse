package com.veltusstudio.veltpulse.player

object AutoEqCatalog {
    val profiles = listOf(
        EqPreset("Nothing Ear (2024)", listOf(1.5f, 0.5f, -1.0f, -1.5f, 0f, 1.5f, 2.5f, 1.5f, -1.0f, 0.5f)),
        EqPreset("Nothing Ear (a)", listOf(0.5f, -1.0f, -2.0f, -1.5f, 0.5f, 2.0f, 2.5f, 1.0f, 0f, 0.5f)),
        EqPreset("Sony WH-1000XM5", listOf(-3.5f, -2.5f, -1.5f, 0f, 1.5f, 2.5f, 3.0f, 2.0f, 1.5f, 1.0f)),
        EqPreset("AirPods Pro 2", listOf(1.5f, 1.0f, 0f, -0.5f, 0f, 1.0f, 2.0f, 1.5f, 0.5f, 0f)),
        EqPreset("Moondrop Chu II", listOf(1.0f, 0.5f, 0f, -0.5f, 0.5f, 1.0f, 0.5f, -1.0f, 0.5f, 1.0f)),
        EqPreset("Sennheiser HD 600", listOf(5.0f, 3.5f, 1.5f, 0f, -0.5f, 0f, -1.0f, -0.5f, 1.0f, 1.5f)),
        EqPreset("Galaxy Buds2 Pro", listOf(0.5f, 0f, -1.0f, -0.5f, 0.5f, 1.5f, 1.0f, -2.0f, -1.5f, 0.5f)),
        EqPreset("Bose QC Ultra", listOf(-2.5f, -1.5f, -1.0f, 0.5f, 1.5f, 2.0f, 2.5f, 1.5f, 1.0f, 0.5f))
    )
}
