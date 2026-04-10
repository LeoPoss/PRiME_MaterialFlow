package de.ur.operational.verification.model

import kotlinx.serialization.Serializable

@Serializable
data class PRiMEAnnotation(
    val requirements: Requirements
)

@Serializable
data class Requirements(
    val tools: List<ToolEntry> = emptyList(),
    val materials: List<MaterialEntry> = emptyList()
)

@Serializable
data class ToolEntry(
    val toolType: String,
    val brand: String,
    val model: String
)

@Serializable
data class MaterialEntry(
    val materialType: String,
    val materialName: String,
    val materialID: String,
    val requiredQuantity: Double,
    val unitOfMeasurement: String
)
