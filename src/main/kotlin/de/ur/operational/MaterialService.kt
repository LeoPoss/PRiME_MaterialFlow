package de.ur.operational

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import de.ur.operational.model.MaterialRequirement
import de.ur.operational.model.MaterialRequirements
import de.ur.operational.model.TaskMaterialRequirements
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private val logger = KotlinLogging.logger {}

private val json = Json { ignoreUnknownKeys = true; isLenient = true }
private val yamlObjectMapper = ObjectMapper(YAMLFactory())
private val docBuilderFactory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
private val bpmnNs = "http://www.omg.org/spec/BPMN/20100524/MODEL"

@Service
class MaterialService {

    fun extractMaterialRequirements(bpmnPath: String): List<TaskMaterialRequirements> {
        return try {
            val doc = docBuilderFactory.newDocumentBuilder().parse(File(bpmnPath)).apply {
                documentElement.normalize()
            }

            val textAnnotations = doc.getElementsByTagNameNS(bpmnNs, "textAnnotation")
            val associations = doc.getElementsByTagNameNS(bpmnNs, "association")

            val annotationRequirements = mutableMapOf<String, MaterialRequirements>()

            for (i in 0 until textAnnotations.length) {
                val annotation = textAnnotations.item(i) as? Element ?: continue
                val annotationId = annotation.getAttribute("id")

                val textNodes = annotation.getElementsByTagNameNS(bpmnNs, "text")
                val textElement = textNodes.item(0) as? Element ?: continue
                val textContent = textElement.textContent?.trim() ?: continue

                try {
                    val requirements = parseMaterialRequirements(textContent)
                    annotationRequirements[annotationId] = requirements
                } catch (e: Exception) {
                    logger.warn { "Failed to parse material requirements in annotation $annotationId: ${e.message}" }
                }
            }

            val taskRequirementsMap = mutableMapOf<String, MutableList<MaterialRequirement>>()
            for (i in 0 until associations.length) {
                val association = associations.item(i) as? Element ?: continue
                val sourceRef = association.getAttribute("sourceRef")
                val targetRef = association.getAttribute("targetRef")

                annotationRequirements[sourceRef]?.let { annotation ->
                    taskRequirementsMap.getOrPut(targetRef) { mutableListOf() }
                        .addAll(annotation.getRequirements())
                }
            }

            taskRequirementsMap.map { (taskId, requirements) ->
                TaskMaterialRequirements(taskId, requirements)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to extract material requirements from BPMN: ${e.message}" }
            emptyList()
        }
    }

    private fun parseMaterialRequirements(text: String): MaterialRequirements {
        // Try JSON first
        try {
            return json.decodeFromString<MaterialRequirements>(text)
        } catch (e: Exception) {
            logger.debug { "JSON parsing failed, trying YAML: ${e.message}" }
        }
        
        // Fall back to YAML with backward-compatible and new paper format handling
        return try {
            val map = yamlObjectMapper.readValue(text, Map::class.java) as Map<*, *>

            // WF2: paper format check - if root contains 'requirements', parse paper format
            if (map.containsKey("requirements")) {
                val root = map["requirements"] as? Map<*, *>
                    ?: throw IllegalArgumentException("WF2: 'requirements' root must be a map")

                val toolsList = (root["tools"] as? List<*>)?.filterNotNull() ?: emptyList()
                val materialsList = (root["materials"] as? List<*>)?.filterNotNull() ?: emptyList()

                val results = mutableListOf<MaterialRequirement>()

                // Map tools -> MaterialRequirement with resourceType = 'Tool'
                for (toolObj in toolsList) {
                    val toolMap = toolObj as? Map<*, *>
                    val toolType = toolMap?.get("toolType") as? String
                    val resourceName = toolMap?.get("resourceName") as? String ?: toolType
                    val resourceID = toolMap?.get("resourceID") as? String
                    val requiredQuantity = (toolMap?.get("requiredQuantity") as? Number)?.toDouble() ?: 1.0
                    val unit = toolMap?.get("unitOfMeasurement") as? String

                    if (toolType != null) {
                        results.add(
                            MaterialRequirement(
                                resourceType = "Tool",
                                resourceID = resourceID,
                                resourceName = resourceName,
                                requiredQuantity = requiredQuantity,
                                unitOfMeasurement = unit
                            )
                        )
                    }
                }

                // Map materials -> MaterialRequirement
                for (matObj in materialsList) {
                    val matMap = matObj as? Map<*, *>
                    val resourceName = matMap?.get("materialName") as? String
                    val requiredQuantity = (matMap?.get("requiredQuantity") as? Number)?.toDouble()
                    val resourceType = (matMap?.get("materialType") as? String) ?: "RawMaterial"
                    val resourceID = matMap?.get("materialID") as? String
                    val unit = matMap?.get("unitOfMeasurement") as? String

                    if (resourceName != null && requiredQuantity != null) {
                        results.add(
                            MaterialRequirement(
                                resourceType = resourceType,
                                resourceID = resourceID,
                                resourceName = resourceName,
                                requiredQuantity = requiredQuantity,
                                unitOfMeasurement = unit
                            )
                        )
                    }
                }

                MaterialRequirements(resourceRequirements = results)
            } else {
                // WF2: existing resourceRequirements root (backward compatibility)
                val requirementsList = map["resourceRequirements"] as? List<*>
                val materialRequirements = requirementsList?.mapNotNull { item ->
                    val itemMap = item as? Map<*, *>
                    itemMap?.let { map ->
                        MaterialRequirement(
                            resourceType = map["resourceType"] as? String,
                            resourceID = map["resourceID"] as? String,
                            resourceName = map["resourceName"] as? String,
                            requiredQuantity = (map["requiredQuantity"] as? Number)?.toDouble() ?: 0.0,
                            unitOfMeasurement = map["unitOfMeasurement"] as? String
                        )
                    }
                }
                MaterialRequirements(resourceRequirements = materialRequirements)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse YAML paper-format or legacy JSON/YAML: ${e.message}")
        }
    }
}
