package de.ur.operational

import io.github.oshai.kotlinlogging.KotlinLogging
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private val logger = KotlinLogging.logger {}

private const val BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL"
private val TASK_TYPES = listOf("task", "userTask", "manualTask", "serviceTask", "scriptTask")
private val GATEWAY_TYPES = listOf("exclusiveGateway", "parallelGateway", "inclusiveGateway", "eventBasedGateway")

/**
 * Processes a BPMN file to extract task order and material requirements.
 */
class BpmnProcessor(private val xmlFilePath: String) {

    /**
     * Loads the task order from the BPMN file.
     * @return Ordered list of task IDs
     * @throws IllegalArgumentException if the BPMN file is not found or invalid
     */
    fun loadTaskOrder(): List<String> = File(xmlFilePath).let { file ->
        require(file.exists()) { "BPMN file not found at $xmlFilePath" }

        runCatching {
            parseBpmnFile(file).let { doc ->
                val tasks = doc.extractAllTasks()
                val sequenceFlows = doc.extractSequenceFlows()
                val startTask = findStartTask(doc, sequenceFlows)
                    ?: error("No valid start task found in BPMN")

                doc.determineTaskOrder(tasks, sequenceFlows, startTask)
            }
        }.onSuccess { taskOrder ->
            logger.info { "Extracted task order: $taskOrder" }
        }.onFailure { e ->
            logger.error(e) { "Error processing BPMN file: ${e.message}" }
        }.getOrThrow()
    }

    private fun parseBpmnFile(file: File): Document =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
            .also { it.documentElement.normalize() }

    private fun Document.extractAllTasks(): Map<String, String> =
        TASK_TYPES.flatMap { extractTasks(it) }
            .toMap()

    private fun Document.extractTasks(tagName: String): List<Pair<String, String>> =
        getBpmnElements(tagName)
            .map {
                val id = it.getAttribute("id")
                val name = it.getAttribute("name").takeIf(String::isNotBlank) ?: id
                id to name
            }
            .toList()

    private fun Document.extractSequenceFlows(): Map<String, List<String>> =
        getBpmnElements("sequenceFlow")
            .groupBy(
                keySelector = { it.getAttribute("sourceRef") },
                valueTransform = { it.getAttribute("targetRef") }
            )

    private fun findStartTask(doc: Document, sequenceFlows: Map<String, List<String>>): String? {
        val startEvents = doc.getBpmnElements("startEvent")
            .map { it.getAttribute("id") }
            .toSet()
        // Take first found start event
        return sequenceFlows.keys.firstOrNull { it in startEvents }
    }

    private fun Document.determineTaskOrder(
        tasks: Map<String, String>,
        sequenceFlows: Map<String, List<String>>,
        startTask: String
    ): List<String> {
        val orderedTasks = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>().apply { add(startTask) }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            if (current !in visited) {
                visited += current

                // Process current node
                if (current in tasks) {
                    orderedTasks += current
                } else if (!isGateway(current)) {
                    logger.warn { "Encountered unknown node '$current' in process flow" }
                }

                // Queue unvisited next nodes (could implement more sophisticated traversal in future)
                sequenceFlows[current]?.let { next ->
                    queue.addAll(next.filter { it !in visited })
                }
            }
        }
        return orderedTasks
    }

    private fun Document.isGateway(elementId: String): Boolean =
        GATEWAY_TYPES.any { type ->
            getBpmnElements(type)
                .any { it.getAttribute("id") == elementId }
        }

    /**
     * Gets elements by tag name within the BPMN namespace and converts them to a sequence of Elements.
     */
    private fun Document.getBpmnElements(tagName: String): Sequence<Element> {
        val nodeList = getElementsByTagNameNS(BPMN_NAMESPACE, tagName)
        return (0 until nodeList.length).asSequence()
            .map { nodeList.item(it) as? Element }
            .filterNotNull()
    }
}