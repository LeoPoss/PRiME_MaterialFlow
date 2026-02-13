package de.ur.operational.model

data class MaterialRequirement(
    val materialType: String,
    val materialID: String? = null,
    val materialName: String,
    val requiredQuantity: Double,
    val unitOfMeasurement: String? = null,
)

data class MaterialRequirements(
    val materialRequirements: List<MaterialRequirement>
)

data class TaskMaterialRequirements(
    val taskId: String,
    val requirements: List<MaterialRequirement>
)
