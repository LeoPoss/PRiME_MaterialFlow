package de.ur.operational.process.listener

import de.ur.operational.process.model.ResourceInventory
import de.ur.operational.process.model.ResourceObject
import de.ur.operational.process.util.JsonUtil
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component

@Component
class InventoryUpdateWithMissingResourcesListener : JavaDelegate {

    override fun execute(execution: DelegateExecution) {
        val inventory = JsonUtil.fromJson<ResourceInventory>(
            execution.getVariable("ResourceInventory") as? String
        ) ?: throw IllegalStateException("ResourceInventory variable not set")

        val missingResources = JsonUtil.fromJson<List<ResourceObject>>(
            execution.getVariable("MissingResourceObject") as? String
        ) ?: return

        missingResources.forEach { missing ->
            val item = findOrCreateItem(inventory, missing)
            val updatedItem = item.copy(quantity = item.quantity + missing.quantity)
            val index = inventory.resources.indexOf(item)
            if (index >= 0) {
                inventory.resources[index] = updatedItem
            }
        }

        execution.setVariable("ResourceInventory", JsonUtil.toJson(inventory))
        execution.setVariable("MissingResourceObject", "[]")
    }

    private fun findOrCreateItem(inventory: ResourceInventory, missing: ResourceObject): ResourceObject {
        val existing = inventory.resources.find { it.resourceId == missing.resourceId && missing.resourceId != null }
            ?: inventory.resources.find { it.resourceName == missing.resourceName && it.type == missing.type }
        
        return existing ?: ResourceObject(
            resourceName = missing.resourceName,
            resourceId = missing.resourceId,
            quantity = 0.0,
            unitOfMeasurement = missing.unitOfMeasurement,
            type = missing.type
        ).also { inventory.resources.add(it) }
    }
}
