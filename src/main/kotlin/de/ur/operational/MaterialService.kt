package de.ur.operational

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.ur.operational.model.MaterialRequirement
import de.ur.operational.model.MaterialRequirements
import de.ur.operational.model.TaskMaterialRequirements
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private val logger = KotlinLogging.logger {}

@Service
class MaterialService {
    private val jsonMapper = ObjectMapper().registerKotlinModule()
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    init {
        // added for unknown/unparsable parameters
        jsonMapper.findAndRegisterModules()
        yamlMapper.findAndRegisterModules()
    }

    /**
     * Extracts material requirements from the BPMN file.
     */
    fun extractMaterialRequirements(bpmnPath: String): List<TaskMaterialRequirements> {
        //val xmlFilePath = "src/main/resources/processes/MaterialFlow.bpmn"
        val taskRequirements = mutableListOf<TaskMaterialRequirements>()

        try {
            val xmlFile = File(bpmnPath)
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val doc = xmlFile.inputStream().use { inputStream ->
                factory.newDocumentBuilder().parse(inputStream).apply {
                    documentElement.normalize()
                }
            }

            // Get all text annotations and associations
            val textAnnotations =
                doc.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "textAnnotation")
            val associations = doc.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "association")

            // Create a map of annotation ID to its parsed material requirements
            val annotationRequirements = mutableMapOf<String, MaterialRequirements>()

            // First pass: parse all text annotations with material requirements
            textAnnotations.asSequence().filterIsInstance<Element>() // Filter and cast to Element in one step.
                .forEach { annotation ->
                    val annotationId = annotation.getAttribute("id")

                    // Use a safe cast and a let block to process only if a valid text element exists.
                    val textElement =
                        annotation.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "text")
                            .item(0) as? Element

                    textElement?.textContent?.let { textContent ->
                        try {
                            val cleanedText = cleanInputText(textContent)
                            val requirements = parseMaterialRequirements(cleanedText)
                            annotationRequirements[annotationId] = requirements
                        } catch (e: Exception) {
                            logger.error { "Failed to parse material requirements in annotation $annotationId: ${e.message}" }
                        }
                    }
                }

            // Second pass: find associations between annotations and tasks
            val taskRequirementsMap = associations.asSequence().filterIsInstance<Element>()
                .fold(mutableMapOf<String, MutableList<MaterialRequirement>>()) { acc, association ->
                    val sourceRef = association.getAttribute("sourceRef")
                    val targetRef = association.getAttribute("targetRef")

                    annotationRequirements[sourceRef]?.let { annotation ->
                        acc.getOrPut(targetRef) { mutableListOf() }.addAll(annotation.materialRequirements)
                    }
                    acc
                }

            // Convert to final result format
            taskRequirementsMap.forEach { (taskId, requirements) ->
                taskRequirements.add(TaskMaterialRequirements(taskId, requirements))
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return taskRequirements
    }

    /**
     * Cleans the input text by removing non-breaking spaces and other problematic whitespace.
     */
    private fun cleanInputText(text: String): String {
        // Replace non-breaking spaces and other problematic whitespace
        return text.trim().replace("&nbsp;", " ").replace("\u00A0", " ").replace("\uFEFF", "").replace("\r\n", "\n")
            .lines().joinToString("\n") { it.trimEnd() }
    }

    /**
     * Parses the material requirements from the given text.
     */
    private fun parseMaterialRequirements(text: String): MaterialRequirements {
        return try {
            // First try to parse as JSON
            jsonMapper.readValue(text, MaterialRequirements::class.java)
        } catch (jsonError: Exception) {
            try {
                // If JSON parsing fails, try YAML
                yamlMapper.readValue(text, MaterialRequirements::class.java)
            } catch (yamlError: Exception) {
                throw IllegalArgumentException(
                    "Failed to parse material requirements. Content must be valid JSON or YAML. " + "JSON error: ${jsonError.message}, YAML error: ${yamlError.message}"
                )
            }
        }
    }

    fun NodeList.asSequence(): Sequence<Node> = sequence {
        for (i in 0 until length) {
            yield(item(i))
        }
    }
}