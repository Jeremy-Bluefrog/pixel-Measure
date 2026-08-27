package com.example.logic.ai

import kotlinx.serialization.Serializable

@Serializable
data class AiTilePreset(
    val name: String,
    val category: String, // 地磚, 壁磚, 大板磚, 木紋磚, 異形馬賽克
    val widthCm: Double,
    val heightCm: Double,
    val defaultMaterial: String,
    val defaultGroutMm: Double = 2.0,
    val standardBoxPieces: Int = 4, // 一箱幾片
    val unitPriceEstTwd: Double = 180.0 // 單片預估價格 (NT$)
) {
    val singleTileAreaM2: Double
        get() = (widthCm * heightCm) / 10000.0

    val areaPerBoxM2: Double
        get() = singleTileAreaM2 * standardBoxPieces

    companion object {
        val PRESET_60X60 = AiTilePreset(
            name = "標準石英地磚",
            category = "地磚",
            widthCm = 60.0,
            heightCm = 60.0,
            defaultMaterial = "拋光/霧面石英磚",
            defaultGroutMm = 2.0,
            standardBoxPieces = 4,
            unitPriceEstTwd = 220.0
        )

        val PRESET_80X80 = AiTilePreset(
            name = "大氣客廳地磚",
            category = "地磚",
            widthCm = 80.0,
            heightCm = 80.0,
            defaultMaterial = "全釉拋大理石紋磚",
            defaultGroutMm = 2.0,
            standardBoxPieces = 3,
            unitPriceEstTwd = 450.0
        )

        val PRESET_30X60 = AiTilePreset(
            name = "現代衛浴壁磚",
            category = "壁磚",
            widthCm = 30.0,
            heightCm = 60.0,
            defaultMaterial = "高亮釉修邊壁磚",
            defaultGroutMm = 2.0,
            standardBoxPieces = 8,
            unitPriceEstTwd = 110.0
        )

        val PRESET_30X30 = AiTilePreset(
            name = "防滑浴室地磚",
            category = "地磚",
            widthCm = 30.0,
            heightCm = 30.0,
            defaultMaterial = "止滑石英小地磚",
            defaultGroutMm = 3.0,
            standardBoxPieces = 15,
            unitPriceEstTwd = 45.0
        )

        val PRESET_20X120 = AiTilePreset(
            name = "溫潤木紋磚",
            category = "木紋磚",
            widthCm = 20.0,
            heightCm = 120.0,
            defaultMaterial = "原木雕刻木紋磚",
            defaultGroutMm = 1.5,
            standardBoxPieces = 5,
            unitPriceEstTwd = 320.0
        )

        val PRESET_60X120 = AiTilePreset(
            name = "大板薄板精品磚",
            category = "大板磚",
            widthCm = 60.0,
            heightCm = 120.0,
            defaultMaterial = "義大利連紋薄板",
            defaultGroutMm = 1.5,
            standardBoxPieces = 2,
            unitPriceEstTwd = 880.0
        )

        val PRESET_10X30 = AiTilePreset(
            name = "經典北歐地鐵磚",
            category = "壁磚",
            widthCm = 10.0,
            heightCm = 30.0,
            defaultMaterial = "斜角地鐵壁磚",
            defaultGroutMm = 2.5,
            standardBoxPieces = 30,
            unitPriceEstTwd = 25.0
        )

        val STANDARD_PRESETS = listOf(
            PRESET_60X60,
            PRESET_80X80,
            PRESET_30X60,
            PRESET_30X30,
            PRESET_20X120,
            PRESET_60X120,
            PRESET_10X30
        )
    }
}

enum class TilePatternType(val label: String, val description: String, val wastageOffsetPct: Int) {
    GRID("十字對齊 (標準網格)", "最經典的平鋪排列，損耗最低", 0),
    BRICK_HALF("二八/交錯交丁 (磚形)", "常用於木紋磚與壁磚，增加視覺層次", 3),
    HERRINGBONE("人字拼 (斜向美學)", "高級感人字斜向排列，邊角裁切損耗較大", 8)
}

@Serializable
data class ComprehensiveTileBudget(
    val tilePreset: AiTilePreset,
    val patternType: TilePatternType,
    val roomAreaM2: Double,
    val baseTileCount: Int,
    val wastagePercentage: Int,
    val totalTileCount: Int,
    val totalBoxesCount: Int,
    val estimatedGroutLengthMeters: Double,
    val estimatedGroutBags2kg: Int,
    val estimatedTileCostTwd: Double,
    val estimatedGroutCostTwd: Double,
    val estimatedInstallationLaborTwd: Double,
    val totalEstimatedBudgetTwd: Double
)
