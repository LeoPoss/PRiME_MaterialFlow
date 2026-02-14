package de.ur.operational.process.controller

import de.ur.operational.process.model.ResourceInventory
import de.ur.operational.process.util.JsonUtil
import org.camunda.bpm.engine.RuntimeService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/resources")
class ResourceController(
    private val runtimeService: RuntimeService
) {

    @PostMapping("/inventory/update")
    fun updateInventory(@RequestBody inventory: ResourceInventory): ResponseEntity<String> {
        val processInstances = runtimeService.createProcessInstanceQuery()
            .processDefinitionKey("trussPrefabricationProcess")
            .active()
            .list()

        if (processInstances.isEmpty()) {
            return ResponseEntity.badRequest().body("No active process instances found")
        }

        val inventoryJson = JsonUtil.toJson(inventory)

        processInstances.forEach { instance ->
            runtimeService.setVariable(instance.id, "ResourceInventory", inventoryJson)
        }

        return ResponseEntity.ok("Inventory updated for ${processInstances.size} process instance(s)")
    }

    @GetMapping("/inventory")
    fun getInventory(): ResponseEntity<ResourceInventory> {
        val processInstances = runtimeService.createProcessInstanceQuery()
            .processDefinitionKey("trussPrefabricationProcess")
            .active()
            .list()

        if (processInstances.isEmpty()) {
            return ResponseEntity.ok(ResourceInventory())
        }

        val inventoryJson = runtimeService.getVariable(
            processInstances.first().id,
            "ResourceInventory"
        ) as? String

        return inventoryJson?.let {
            JsonUtil.fromJson<ResourceInventory>(it)?.let { inventory ->
                ResponseEntity.ok(inventory)
            }
        } ?: ResponseEntity.ok(ResourceInventory())
    }
}
