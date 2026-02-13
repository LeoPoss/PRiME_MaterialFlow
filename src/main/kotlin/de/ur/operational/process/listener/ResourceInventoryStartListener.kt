package de.ur.operational.process.listener

import de.ur.operational.process.model.ResourceInventory
import de.ur.operational.process.model.ResourceObject
import de.ur.operational.process.util.JsonUtil
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.ExecutionListener
import org.springframework.stereotype.Component

@Component
class ResourceInventoryStartListener : ExecutionListener {

    override fun notify(execution: DelegateExecution) {
        val inventory = ResourceInventory(resources = mutableListOf(
            resource("Corner Connector", "EV-001", "Connector"),
            resource("Screw M8", "M8-001", "Screws"),
            resource("Side Rail", "SR-001", "WoodenComponent"),
            resource("Screw M10", "M10-001", "Screws"),
            resource("Table Leg", "TL-001", "LegComponent")
        ))

        execution.setVariable("ResourceInventory", JsonUtil.toJson(inventory))
    }

    private fun resource(name: String, id: String, type: String) = ResourceObject(
        resourceName = name,
        resourceId = id,
        quantity = 4.0,
        unitOfMeasurement = "pieces",
        type = type
    )
}
