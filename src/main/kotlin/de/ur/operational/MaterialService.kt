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
        
        // Fall back to YAML
        return try {
            val map = yamlObjectMapper.readValue(text, Map::class.java)
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
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse both JSON and YAML: ${e.message}")
        }
    }
}