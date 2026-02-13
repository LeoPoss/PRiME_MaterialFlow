package de.ur.operational.process.delegate

import de.ur.operational.process.model.*
import de.ur.operational.process.util.JsonUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class InventoryUpdateDelegate : JavaDelegate {

    override fun execute(execution: DelegateExecution) {
        val lastCompletedTask = execution.getVariable("lastCompletedTask") as? String
            ?: return

        val taskReq = JsonUtil.fromJson<ResourceRequirementObject>(
            execution.getVariable("${lastCompletedTask}_ResourceRequirement") as? String
        ) ?: run {
            logger.warn { "No resource requirements found for completed task: $lastCompletedTask" }
            return
        }

        val inventory = JsonUtil.fromJson<ResourceInventory>(
            execution.getVariable("ResourceInventory") as? String
        ) ?: throw IllegalStateException("ResourceInventory variable not set")

        taskReq.resourceRequirements.forEach { req ->
            if (req is ResourceTypeRequirement && req.resourceType == "Intermediate") return@forEach

            val itemIndex = inventory.resources.indexOfFirst { res ->
                when (req) {
                    is ResourceSpecificationRequirement -> res.resourceId == req.resourceID
                    is ResourceTypeRequirement -> res.type == req.resourceType && res.resourceName == req.resourceName
                    else -> false
                }
            }
            if (itemIndex == -1) return@forEach

            val item = inventory.resources[itemIndex]
            val newQuantity = (item.quantity - req.requiredQuantity).coerceAtLeast(0.0)
            inventory.resources[itemIndex] = item.copy(quantity = newQuantity)
        }

        execution.setVariable("ResourceInventory", JsonUtil.toJson(inventory))
    }
}
