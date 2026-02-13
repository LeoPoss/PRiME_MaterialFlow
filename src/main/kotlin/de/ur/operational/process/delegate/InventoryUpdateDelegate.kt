package de.ur.operational.process.delegate

import de.ur.operational.process.model.*
import de.ur.operational.process.util.JsonUtil
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component

@Component
class InventoryUpdateDelegate : JavaDelegate {

    override fun execute(execution: DelegateExecution) {
        val lastCompletedTask = execution.getVariable("lastCompletedTask") as? String
            ?: return

        val taskReq = JsonUtil.fromJson<ResourceRequirementObject>(
            execution.getVariable("${lastCompletedTask}_ResourceRequirement") as? String
        ) ?: run {
            println("No resource requirements found for completed task: $lastCompletedTask")
            return
        }

        val inventory = JsonUtil.fromJson<ResourceInventory>(
            execution.getVariable("ResourceInventory") as? String
        ) ?: throw IllegalStateException("ResourceInventory variable not set")

        taskReq.resourceRequirements.forEach { req ->
            if (req is ResourceTypeRequirement && req.resourceType == "Intermediate") return@forEach

            val item = findInventoryItem(inventory, req) ?: return@forEach
            item.quantity = (item.quantity - req.requiredQuantity).coerceAtLeast(0.0)
        }

        execution.setVariable("ResourceInventory", JsonUtil.toJson(inventory))
    }

    private fun findInventoryItem(inventory: ResourceInventory, req: ResourceRequirement): ResourceObject? =
        when (req) {
            is ResourceSpecificationRequirement -> inventory.resources.find { it.resourceId == req.resourceID }
            is ResourceTypeRequirement -> inventory.resources.find { it.type == req.resourceType && it.resourceName == req.resourceName }
            else -> null
        }
}
