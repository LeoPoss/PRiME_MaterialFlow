package de.ur.operational.process.model

import com.fasterxml.jackson.annotation.*

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@JsonSubTypes(
    JsonSubTypes.Type(value = ResourceSpecificationRequirement::class, name = "specification"),
    JsonSubTypes.Type(value = ResourceTypeRequirement::class, name = "type")
)
abstract class ResourceRequirement(
    open val requiredQuantity: Double = 0.0,
    open val unitOfMeasurement: String = "pieces"
) {
    abstract val resourceName: String

    companion object {
        @JsonCreator
        @JvmStatic
        fun fromJson(
            @JsonProperty("resourceID") resourceID: String?,
            @JsonProperty("resourceType") resourceType: String?,
            @JsonProperty("resourceName") resourceName: String,
            @JsonProperty("requiredQuantity") requiredQuantity: Double,
            @JsonProperty("unitOfMeasurement") unitOfMeasurement: String
        ): ResourceRequirement = if (resourceID == null) {
            ResourceTypeRequirement(
                resourceType = resourceType ?: "",
                resourceName = resourceName,
                requiredQuantity = requiredQuantity,
                unitOfMeasurement = unitOfMeasurement
            )
        } else {
            ResourceSpecificationRequirement(
                resourceID = resourceID,
                resourceName = resourceName,
                requiredQuantity = requiredQuantity,
                unitOfMeasurement = unitOfMeasurement
            )
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResourceSpecificationRequirement(
    val resourceID: String,
    override val resourceName: String,
    override val requiredQuantity: Double,
    override val unitOfMeasurement: String
) : ResourceRequirement(requiredQuantity, unitOfMeasurement)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResourceTypeRequirement(
    val resourceType: String,
    override val resourceName: String,
    override val requiredQuantity: Double,
    override val unitOfMeasurement: String
) : ResourceRequirement(requiredQuantity, unitOfMeasurement)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResourceRequirementCollection(
    val requirements: List<ResourceRequirement> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResourceRequirementObject(
    val taskId: String,
    val resourceRequirements: List<ResourceRequirement> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResourceRequirementAnnotation(
    val id: String? = null,
    val resourceRequirements: List<ResourceRequirement> = emptyList()
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResourceObject(
    val resourceName: String,
    val resourceId: String? = null,
    val quantity: Double,
    val unitOfMeasurement: String,
    val type: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResourceInventory(
    val resources: MutableList<ResourceObject> = mutableListOf()
)
