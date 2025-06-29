package de.ur.operational

import de.ur.operational.model.SankeyData
import de.ur.operational.model.SankeyLink
import de.ur.operational.model.SankeyNode
import de.ur.operational.model.TaskMaterialRequirements
import org.springframework.stereotype.Service

@Service
class SankeyService(private val modelService: ModelService, private val materialService: MaterialService) {
    /**
     * Generates sankey diagram data based on the task order and material requirements.
     */
    fun generateSankeyData(): SankeyData {
        val taskOrder = modelService.loadTaskOrder()

        val listTaskRequirements = materialService.extractMaterialRequirements()

        val (intermediateMaterialRequirements, materialRequirements) =
            listTaskRequirements.flatMap { it.requirements }
                .map { it.materialName to it.materialType }.distinct()
                .partition { (_, materialType) -> materialType.lowercase().trim() == "intermediate" }
                .let { (intermediates, others) ->
                    intermediates.map { it.first }.distinct() to others.map { it.first }.distinct()
                }

        val tailwind400Colors = listOf(
            "#f43f5e", // rose-500
            "#0ea5e9", // sky-500
            "#f59e0b", // amber-500
            "#84cc16", // lime-500
            "#22c55e", // green-500
            "#d946ef", // fuchsia-500
            "#06b6d4", // cyan-500
            "#10b981", // emerald-500
            "#3b82f6", // blue-500
            "#6366f1", // indigo-500
            "#8b5cf6", // violet-500
            "#eab308", // yellow-500
            "#a855f7", // purple-500
            "#ec4899", // pink-500
            "#14b8a6", // teal-500
            "#ef4444", // red-500
        )

        // Create a map of material types to colors
        val typeToColorMap = listTaskRequirements.flatMap { it.requirements }.map { it.materialType }.distinct()
            .mapIndexed { index, type ->
                type to tailwind400Colors[index % tailwind400Colors.size]
            }.toMap().toMutableMap()

        typeToColorMap["default"] = "#737373" // gray-400

        // collect all material Nodes
        val materialNodes = materialRequirements.map { materialName ->
            val materialType = listTaskRequirements.flatMap { it.requirements }
                .firstOrNull { it.materialName == materialName }?.materialType ?: "default"
            val color = typeToColorMap[materialType] ?: typeToColorMap["default"]!!
            SankeyNode(materialName, materialName, color)
        }


        // Combine material nodes with finished product and nodes for tasks (middle nodes)
        val nodes = materialNodes +
                SankeyNode("FinishedGood", "endEvent", "#737373") +
                taskOrder.map { taskId ->
                    SankeyNode("", taskId)
                }

        // add flows
        val links = buildList {
            var previousTask: String? = ""
            listTaskRequirements.filter { it.taskId in taskOrder }.forEach { taskReq ->
                taskReq.requirements.forEach { requirement ->
                    val materialName = requirement.materialName
                    val requiredQuantity = requirement.requiredQuantity
                    val link = SankeyLink(
                        material = materialName,
                        source = if (materialName in intermediateMaterialRequirements) previousTask
                            ?: materialName else materialName,
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
                material = "FinishedGood", source = lastMaterialConsumingTask, target = "endEvent", value = 1
            )
        }

        return SankeyData(
            nodes = nodes,
            links = links,
        )
    }

    private fun findLastMaterialConsumingTask(
        taskExecutionOrder: List<String?>, taskRequirementsMap: List<TaskMaterialRequirements>
    ): String? {
        return taskExecutionOrder.reversed().firstOrNull { taskId ->
            taskId != null && taskRequirementsMap.any { it.taskId == taskId }
        }
    }
}