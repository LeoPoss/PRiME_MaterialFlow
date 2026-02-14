package de.ur.operational.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MaterialRequirement(
    @SerialName("resourceType")
    val resourceType: String? = null,
    @SerialName("resourceID")
    val resourceID: String? = null,
    @SerialName("resourceName")
    val resourceName: String? = null,
    val requiredQuantity: Double,
    @SerialName("unitOfMeasurement")
    val unitOfMeasurement: String? = null,
) {
    fun getName(): String = resourceName ?: ""
}

@Serializable
data class MaterialRequirements(
    @SerialName("resourceRequirements")
    val resourceRequirements: List<MaterialRequirement>? = null
) {
    fun getRequirements(): List<MaterialRequirement> = resourceRequirements ?: emptyList()
}

@Serializable
data class TaskMaterialRequirements(
    val taskId: String,
    val requirements: List<MaterialRequirement>
)
