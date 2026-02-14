package de.ur.operational.process.delegate

import de.ur.operational.process.model.*
import de.ur.operational.process.util.JsonUtil
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.camunda.bpm.model.bpmn.instance.Association
import org.camunda.bpm.model.bpmn.instance.TextAnnotation
import org.springframework.stereotype.Component

@Component
class CreateResourceListDelegate : JavaDelegate {
    
    override fun execute(execution: DelegateExecution) {
        val modelInstance = execution.getBpmnModelInstance()
        val textAnnotations = modelInstance.getModelElementsByType(TextAnnotation::class.java)
        val associations = modelInstance.getModelElementsByType(Association::class.java)
        
        val allRequirements = mutableListOf<ResourceRequirement>()
        val taskRequirementsMap = mutableMapOf<String, List<ResourceRequirement>>()
        
        textAnnotations.forEach { annotation ->
            parseAnnotation(annotation.textContent)?.let { parsed ->
                allRequirements += parsed.resourceRequirements
                
                associations
                    .filter { it.source == annotation }
                    .mapNotNull { it.target?.id }
                    .forEach { targetId ->
                        taskRequirementsMap.merge(targetId, parsed.resourceRequirements) { existing, new ->
                            existing + new
                        }
                    }
            }
        }
        
        execution.setVariable("resourceRequirementCollection", 
            JsonUtil.toJson(ResourceRequirementCollection(allRequirements)))
        execution.setVariable("formattedResourceList", formatResourceList(allRequirements))
        
        taskRequirementsMap.forEach { (taskId, requirements) ->
            execution.setVariable("${taskId}_ResourceRequirement",
                JsonUtil.toJson(ResourceRequirementObject(taskId, requirements)))
        }
    }
    
    private fun parseAnnotation(text: String): ResourceRequirementAnnotation? =
        runCatching {
            JsonUtil.fromJson<ResourceRequirementAnnotation>(text.trim())
        }.onFailure { 
            println("Failed to parse annotation: ${it.message}") 
        }.getOrNull()
    
    private fun formatResourceList(requirements: List<ResourceRequirement>): String {
        if (requirements.isEmpty()) return "No resources required"
        
        val tools = requirements.filter { it is ResourceTypeRequirement && it.resourceType == "Tool" }
        val materials = requirements.filter { !(it is ResourceTypeRequirement && it.resourceType == "Tool") }
        
        val unitDisplay = { unit: String -> 
            if (unit == "pieces") "×" else unit
        }
        
        return buildString {
            if (tools.isNotEmpty()) {
                appendLine("Tools:")
                tools.forEach { req: ResourceRequirement ->
                    val qty = "${req.requiredQuantity.toInt()} ${unitDisplay(req.unitOfMeasurement)}"
                    appendLine("  - $qty ${req.resourceName}")
                }
                appendLine()
            }
            
            if (materials.isNotEmpty()) {
                appendLine("Materials:")
                materials.forEach { req: ResourceRequirement ->
                    val qty = "${req.requiredQuantity.toInt()} ${unitDisplay(req.unitOfMeasurement)}"
                    val id = when (req) {
                        is ResourceSpecificationRequirement -> " (${req.resourceID})"
                        else -> ""
                    }
                    appendLine("  - $qty ${req.resourceName}$id")
                }
                appendLine()
            }
            
            appendLine("Total: ${requirements.size} items")
        }
    }
}
