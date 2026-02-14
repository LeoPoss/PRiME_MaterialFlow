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
            resource("Table Leg", "TL-001", "LegComponent"),
            resource("Pine Beam 150x200mm", "PB-150", "RawMaterial", 0.0, "meters"),
            resource("Rafter", "RF-001", "RawMaterial", 50.0, "pieces"),
            resource("Structural Screw 10x200mm", "S-10x200", "Consumable", 500.0, "pieces"),
            resource("Angle Bracket 90mm", "C-90-L", "Connector", 100.0, "pieces"),
            resource("Nail 80mm", "NL-80", "Consumable", 50.0, "kg"),
            resource("Circular Saw", "CS-001", "Tool", 1.0, "pieces"),
            resource("Cordless Drill", "CD-001", "Tool", 1.0, "pieces"),
            resource("Crane", "CR-001", "Tool", 1.0, "pieces"),
            resource("Impact Driver", "ID-001", "Tool", 1.0, "pieces"),
            resource("Hammer", "HM-001", "Tool", 1.0, "pieces")
        ))

        execution.setVariable("ResourceInventory", JsonUtil.toJson(inventory))
    }

    private fun resource(name: String, id: String, type: String, quantity: Double = 4.0, unit: String = "pieces") = ResourceObject(
        resourceName = name,
        resourceId = id,
        quantity = quantity,
        unitOfMeasurement = unit,
        type = type
    )
}
