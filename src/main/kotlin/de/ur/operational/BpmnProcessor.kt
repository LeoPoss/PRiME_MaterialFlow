package de.ur.operational

import io.github.oshai.kotlinlogging.KotlinLogging
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Processes a BPMN file to extract task order and material requirements.
 */
private val logger = KotlinLogging.logger {}

class BpmnProcessor(private val xmlFilePath: String) {
    private val BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL"

    /**
     * Loads the task order from the BPMN file.
     */
    fun loadTaskOrder(): List<String> {
        val xmlFile = File(xmlFilePath)
        require(xmlFile.exists()) { "BPMN file not found at $xmlFilePath" }

        return runCatching {
            val doc = parseBpmnFile(xmlFile)
            val tasks = doc.extractAllTasks()
            val sequenceFlows = doc.extractSequenceFlows()
            val startTask = findStartTask(doc, sequenceFlows) ?: error("No valid start task found in BPMN")

            doc.determineTaskOrder(tasks, sequenceFlows, startTask)
        }.onSuccess { taskOrder ->
            logger.info { "Extracted task order: $taskOrder" }
        }.onFailure { e ->
            logger.error { "Error processing BPMN file: ${e.message}" }
            e.printStackTrace()
        }.getOrThrow()
    }

    /**
     * Parses the BPMN file and returns a Document object.
     */
    private fun parseBpmnFile(file: File): Document {
        return DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file)
            .also { it.documentElement.normalize() }
    }

    /**
     * Extracts all tasks from the BPMN file.
     */
    private fun Document.extractAllTasks(): Map<String, String> {
        val tasks = mutableMapOf<String, String>()
        listOf("task", "userTask", "manualTask", "serviceTask", "scriptTask").forEach { tagName ->
            tasks.putAll(extractTasks(tagName))
        }
        return tasks
    }

    /**
     * Extracts tasks from the BPMN file.
     */
    private fun Document.extractTasks(tagName: String): Map<String, String> {
        val nodeList = getBpmnElements(tagName)
        return (0 until nodeList.length).asSequence().map { nodeList.item(it) as Element }.associate {
            it.getAttribute("id") to (it.getAttribute("name").takeIf { name -> name.isNotBlank() }
                ?: it.getAttribute("id"))
        }
    }

    /**
     * Extracts sequence flows from the BPMN file.
     */
    private fun Document.extractSequenceFlows(): Map<String, List<String>> {
        val nodeList = getBpmnElements("sequenceFlow")
        return (0 until nodeList.length).asSequence().map { nodeList.item(it) as Element }
            .groupBy(keySelector = { it.getAttribute("sourceRef") }, valueTransform = { it.getAttribute("targetRef") })
    }

    /**
     * Finds the start task in the BPMN file.
     */
    private fun findStartTask(doc: Document, sequenceFlows: Map<String, List<String>>): String? {
        val nodeList = doc.getBpmnElements("startEvent")
        val startEvents =
            (0 until nodeList.length).asSequence().map { (nodeList.item(it) as Element).getAttribute("id") }.toSet()

        return sequenceFlows.keys.firstOrNull { it in startEvents }
    }

    /**
     * Determines the task order in the BPMN file.
     */
    private fun Document.determineTaskOrder(
        tasks: Map<String, String>, sequenceFlows: Map<String, List<String>>, startTask: String
    ): List<String> {
        val orderedTasks = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>().apply { add(startTask) }

        while (queue.isNotEmpty()) {
            val currentTask = queue.removeFirst()
            if (currentTask in visited) continue
            visited.add(currentTask)

            if (isGateway(currentTask)) {
                sequenceFlows[currentTask]?.let { queue.addAll(it) }
                continue
            }

            if (currentTask in tasks) {
                orderedTasks.add(currentTask)
            }
            sequenceFlows[currentTask]?.let { queue.addAll(it) }
        }
        return orderedTasks
    }

    /**
     * Checks if the given element is a gateway.
     */
    private fun Document.isGateway(elementId: String): Boolean {
        val gatewayTypes = listOf("exclusiveGateway", "parallelGateway", "inclusiveGateway", "eventBasedGateway")
        return gatewayTypes.any { type ->
            val nodeList = getBpmnElements(type)
            (0 until nodeList.length).asSequence().map { nodeList.item(it) as Element }
                .any { it.getAttribute("id") == elementId }
        }
    }

    /**
     * Retrieves the BPMN elements from the document.
     */
    private fun Document.getBpmnElements(tagName: String) = getElementsByTagNameNS(BPMN_NAMESPACE, tagName)
}