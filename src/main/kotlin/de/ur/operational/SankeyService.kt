package de.ur.operational

import de.ur.operational.model.SankeyData
import de.ur.operational.model.SankeyLink
import de.ur.operational.model.SankeyNode
import de.ur.operational.model.TaskMaterialRequirements
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class SankeyService(private val modelService: ModelService, private val materialService: MaterialService) {
    /**
     * Generates sankey diagram data based on the task order and material requirements.
     */
    fun generateSankeyData(bpmnPath: String): SankeyData {

        val taskOrder = modelService.loadTaskOrder(bpmnPath)

        val listTaskRequirements = materialService.extractMaterialRequirements(bpmnPath)

        val (intermediateMaterialRequirements, materialRequirements) =
            listTaskRequirements.flatMap { it.requirements }
                .mapNotNull { it.resourceName?.let { name -> name to it.resourceType } }
                .distinct()
                .partition { (_, resourceType) -> resourceType?.lowercase()?.trim() == "intermediate" }
                .let { (intermediates, others) ->
                    intermediates.map { it.first }.distinct() to others.map { it.first }.distinct()
                }

        // collect all material Nodes
        val materialNodes = materialRequirements.mapNotNull { resourceName ->
            listTaskRequirements.flatMap { it.requirements }
                .firstOrNull { it.resourceName == resourceName }?.let { req ->
                    req.resourceType?.let { type ->
                        SankeyNode(resourceName, resourceName, type)
                    }
                }
        }


        // Combine material nodes with finished product and nodes for tasks (middle nodes)
        val nodes = materialNodes +
                SankeyNode("Finished Good", "endEvent", "endEvent") +
                taskOrder.map { taskId ->
                    SankeyNode("", taskId, "task")
                }

        // add flows
        val links = buildList {
            var previousTask: String? = ""
            listTaskRequirements.filter { it.taskId in taskOrder }.forEach { taskReq ->
                taskReq.requirements.forEach { requirement ->
                    val resourceName = requirement.resourceName ?: return@forEach
                    val requiredQuantity = requirement.requiredQuantity
                    val link = SankeyLink(
                        material = resourceName,
                        source = if (resourceName in intermediateMaterialRequirements) previousTask
                            ?: resourceName else resourceName,
                        target = taskReq.taskId,
                        value = requiredQuantity,
                        unit = requirement.unitOfMeasurement
                    )
                    add(link)
                }
                previousTask = taskReq.taskId
            }
        }.toMutableList()

        //  add last material consuming task and connect with final product
        val lastMaterialConsumingTask = findLastMaterialConsumingTask(taskOrder, listTaskRequirements)
        if (lastMaterialConsumingTask != null) {
            links += SankeyLink(
                material = "Finished Good", source = lastMaterialConsumingTask, target = "endEvent", value = 1.0
            )
        }

        return SankeyData(
            nodes = nodes.sortedBy { it.type },
            links = links,
        )
    }

    private fun findLastMaterialConsumingTask(
        taskExecutionOrder: List<String?>, taskRequirementsMap: List<TaskMaterialRequirements>
    ) = taskExecutionOrder.reversed().firstOrNull { taskId ->
        taskRequirementsMap.any { it.taskId == taskId }
    }
}