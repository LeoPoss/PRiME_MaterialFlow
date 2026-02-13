package de.ur.operational.process.delegate

import de.ur.operational.process.model.*
import de.ur.operational.process.util.JsonUtil
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component

@Component
class ResourceCheckDelegate : JavaDelegate {
    
    override fun execute(execution: DelegateExecution) {
        val collection = JsonUtil.fromJson<ResourceRequirementCollection>(
            execution.getVariable("resourceRequirementCollection") as? String
        ) ?: throw IllegalStateException("resourceRequirementCollection variable not set")
        
        val inventory = JsonUtil.fromJson<ResourceInventory>(
            execution.getVariable("ResourceInventory") as? String
        ) ?: throw IllegalStateException("ResourceInventory variable not set")
        
        val missingResources = checkResourceAvailability(collection.requirements, inventory)
        
        execution.setVariable("resourceAvailable", missingResources.isEmpty())
        execution.setVariable("MissingResourceObject", JsonUtil.toJson(missingResources))
        execution.setVariable("formattedMissingResources", 
            missingResources.formatForDisplay() ?: "All resources available")
    }
    
    private fun checkResourceAvailability(
        requirements: List<ResourceRequirement>,
        inventory: ResourceInventory
    ): List<ResourceObject> = requirements.mapNotNull { req ->
        if (req is ResourceTypeRequirement && req.resourceType == "Intermediate") return@mapNotNull null
        
        val inventoryItem = when (req) {
            is ResourceSpecificationRequirement -> 
                inventory.resources.find { it.resourceId == req.resourceID }
            is ResourceTypeRequirement -> 
                inventory.resources.find { it.type == req.resourceType && it.resourceName == req.resourceName }
            else -> null
        }
        
        val missingQty = when {
            inventoryItem == null -> req.requiredQuantity
            inventoryItem.quantity >= req.requiredQuantity -> return@mapNotNull null
            else -> req.requiredQuantity - inventoryItem.quantity
        }
        
        ResourceObject(
            resourceName = req.resourceName,
            resourceId = (req as? ResourceSpecificationRequirement)?.resourceID,
            quantity = missingQty,
            unitOfMeasurement = req.unitOfMeasurement,
            type = (req as? ResourceTypeRequirement)?.resourceType
        )
    }
    
    private fun List<ResourceObject>.formatForDisplay(): String? =
        if (isEmpty()) null else joinToString("\n") { 
            "${it.resourceName}: ${it.quantity} ${it.unitOfMeasurement} missing" 
        }
}
