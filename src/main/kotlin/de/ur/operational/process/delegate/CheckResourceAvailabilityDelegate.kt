package de.ur.operational.process.delegate

import de.ur.operational.process.model.*
import de.ur.operational.process.util.JsonUtil
import org.camunda.bpm.engine.delegate.BpmnError
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component

@Component
class CheckResourceAvailabilityDelegate : JavaDelegate {
    
    override fun execute(execution: DelegateExecution) {
        val nextTask = execution.getVariable("nextTask") as? String
            ?: throw IllegalStateException("nextTask variable not set")
        
        val taskReq = JsonUtil.fromJson<ResourceRequirementObject>(
            execution.getVariable("${nextTask}_ResourceRequirement") as? String
        ) ?: throw IllegalStateException("Resource requirements not found for task: $nextTask")
        
        val inventory = JsonUtil.fromJson<ResourceInventory>(
            execution.getVariable("ResourceInventory") as? String
        ) ?: throw IllegalStateException("ResourceInventory variable not set")
        
        val missingResources = findMissingResources(taskReq.resourceRequirements, inventory)
        
        if (missingResources.isNotEmpty()) {
            execution.setVariable("formattedMissingResources", missingResources.formatForDisplay())
            execution.setVariable("MissingResourceObject", JsonUtil.toJson(missingResources))
            execution.setVariable("resourceAvailable", false)
            throw BpmnError("RESOURCE_SHORTAGE", "Resources missing for task: $nextTask")
        } else {
            execution.setVariable("resourceAvailable", true)
            execution.setVariable("MissingResourceObject", "[]")
            execution.setVariable("formattedMissingResources", "All resources available")
        }
    }
    
    private fun findMissingResources(
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
    
    private fun List<ResourceObject>.formatForDisplay(): String = joinToString("\n") { 
        "${it.resourceName}: ${it.quantity} ${it.unitOfMeasurement} missing" 
    }
}
