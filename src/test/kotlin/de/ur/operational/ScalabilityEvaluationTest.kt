package de.ur.operational

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.io.FileWriter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.sqrt

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScalabilityEvaluationTest {

    private val bpmnProcessor = BpmnProcessor()
    private val materialService = MaterialService()
    private val sankeyService = SankeyService(ModelService(bpmnProcessor), materialService)

    private val df = DecimalFormat("#0.00", DecimalFormatSymbols.getInstance(Locale.US))

    private val ITERATIONS = 10
    private val WARMUP_ITERATIONS = 5

    data class ScalabilityResult(
        val numTasks: Int,
        val numAnnotations: Int,
        val totalResources: Int,
        val taskExtractionMean: Double,
        val taskExtractionSd: Double,
        val materialParsingMean: Double,
        val materialParsingSd: Double,
        val sankeyTransformMean: Double,
        val sankeyTransformSd: Double,
        val totalLatencyMean: Double,
        val totalLatencySd: Double
    )

    private fun generateSyntheticBpmn(
        outputPath: String,
        numTasks: Int,
        numAnnotations: Int,
        resourcesPerAnnotation: Int = 3
    ) {
        val bpmnId = "synthetic_${numTasks}_tasks"
        val processId = "syntheticProcess_${numTasks}"

        val sb = StringBuilder()
        sb.append(
            """<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="$processId" name="Synthetic Test ($numTasks tasks)" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_S0</bpmn:outgoing>
    </bpmn:startEvent>
"""
        )

        var prevElement = "StartEvent_1"
        for (i in 0 until numTasks) {
            val taskId = "Activity_$i"
            val flowId = "Flow_S$i"
            val nextFlowId = if (i < numTasks - 1) "Flow_S${i + 1}" else "Flow_End"

            sb.append("    <bpmn:sequenceFlow id=\"$flowId\" sourceRef=\"$prevElement\" targetRef=\"$taskId\" />\n")
            sb.append(
                """    <bpmn:userTask id="$taskId" name="Task ${i + 1}">
      <bpmn:incoming>$flowId</bpmn:incoming>
      <bpmn:outgoing>$nextFlowId</bpmn:outgoing>
    </bpmn:userTask>
"""
            )
            prevElement = taskId
        }

        sb.append("    <bpmn:sequenceFlow id=\"Flow_S${numTasks - 1}\" sourceRef=\"Activity_${numTasks - 1}\" targetRef=\"EndEvent_1\" />\n")
        sb.append(
            """    <bpmn:endEvent id="EndEvent_1" name="End">
      <bpmn:incoming>Flow_S${numTasks - 1}</bpmn:incoming>
    </bpmn:endEvent>
"""
        )

        val annotationIds = mutableListOf<String>()
        for (i in 0 until numAnnotations) {
            val annotationId = "TextAnnotation_$i"
            annotationIds.add(annotationId)

            val resources = (1..resourcesPerAnnotation).map { r ->
                """{
      "resourceType": "${listOf("Tool", "Equipment", "RawMaterial", "Consumable", "Connector")[r % 5]}",
      "resourceName": "Resource_${i}_$r",
      "requiredQuantity": ${(r + 1) * (i + 1)},
      "unitOfMeasurement": "pieces"
    }""".trimIndent()
            }.joinToString(",\n    ")

            sb.append(
                """    <bpmn:textAnnotation id="$annotationId">
      <bpmn:text>{
  "resourceRequirements": [
    $resources
  ]
}</bpmn:text>
    </bpmn:textAnnotation>
"""
            )
        }

        annotationIds.forEachIndexed { idx, annId ->
            val targetTask = "Activity_${idx % numTasks.coerceAtMost(numAnnotations)}"
            sb.append(
                """    <bpmn:association id="Association_$idx" sourceRef="$annId" targetRef="$targetTask" />
"""
            )
        }

        sb.append(
            """  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="$processId" />
  </bpmndi:BPMNDiagram>
</bpmn:definitions>"""
        )

        File(outputPath).parentFile?.mkdirs()
        FileWriter(outputPath).use { it.write(sb.toString()) }
    }

    private fun runEvaluation(bpmnPath: String): Triple<Double, Double, Double> {
        val taskStart = System.nanoTime()
        bpmnProcessor.loadTaskOrder(bpmnPath)
        val taskTime = (System.nanoTime() - taskStart) / 1_000_000.0

        val materialStart = System.nanoTime()
        val materialReqs = materialService.extractMaterialRequirements(bpmnPath)
        val materialTime = (System.nanoTime() - materialStart) / 1_000_000.0

        val sankeyStart = System.nanoTime()
        sankeyService.generateSankeyData(bpmnPath)
        val sankeyTime = (System.nanoTime() - sankeyStart) / 1_000_000.0

        val totalResources = materialReqs.sumOf { it.requirements.size }
        return Triple(taskTime, materialTime, sankeyTime)
    }

    private fun runAggregatedEvaluation(bpmnPath: String): List<Double> {
        repeat(WARMUP_ITERATIONS) { runEvaluation(bpmnPath) }

        val results = mutableListOf<Triple<Double, Double, Double>>()
        repeat(ITERATIONS) {
            results.add(runEvaluation(bpmnPath))
        }

        return results.map { it.first + it.second + it.third }
    }

    private fun mean(values: List<Double>) = values.sum() / values.size

    private fun sd(values: List<Double>): Double {
        val avg = mean(values)
        val variance = values.map { (it - avg) * (it - avg) }.sum() / values.size
        return sqrt(variance)
    }

    @Test
    fun `scalability evaluation - synthetic data points`() {
        val taskCounts = listOf(10, 50, 100, 200, 500)
        val results = mutableListOf<ScalabilityResult>()

        val tempDir = "build/synthetic-bpmn"
        File(tempDir).mkdirs()

        for (numTasks in taskCounts) {
            val numAnnotations = numTasks / 5
            val bpmnPath = "$tempDir/synthetic_$numTasks.bpmn"

            println(">>> Generating synthetic BPMN with $numTasks tasks, $numAnnotations annotations...")
            generateSyntheticBpmn(bpmnPath, numTasks, numAnnotations, 3)

            println(">>> Running evaluation on $numTasks tasks...")

            repeat(WARMUP_ITERATIONS) { runEvaluation(bpmnPath) }

            val taskTimes = mutableListOf<Double>()
            val materialTimes = mutableListOf<Double>()
            val sankeyTimes = mutableListOf<Double>()

            repeat(ITERATIONS) {
                val (taskT, materialT, sankeyT) = runEvaluation(bpmnPath)
                taskTimes.add(taskT)
                materialTimes.add(materialT)
                sankeyTimes.add(sankeyT)
            }

            val totals = taskTimes.mapIndexed { i, t -> t + materialTimes[i] + sankeyTimes[i] }
            val totalResources = numAnnotations * 3

            val result = ScalabilityResult(
                numTasks = numTasks,
                numAnnotations = numAnnotations,
                totalResources = totalResources,
                taskExtractionMean = mean(taskTimes),
                taskExtractionSd = sd(taskTimes),
                materialParsingMean = mean(materialTimes),
                materialParsingSd = sd(materialTimes),
                sankeyTransformMean = mean(sankeyTimes),
                sankeyTransformSd = sd(sankeyTimes),
                totalLatencyMean = mean(totals),
                totalLatencySd = sd(totals)
            )
            results.add(result)

            println("    Tasks: $numTasks, Annotations: $numAnnotations, Resources: $totalResources")
            println("    Total: ${df.format(result.totalLatencyMean)} ± ${df.format(result.totalLatencySd)} ms")
        }

        val csvPath = "build/reports/scalability-evaluation.csv"
        File("build/reports").mkdirs()
        FileWriter(csvPath).use { writer ->
            writer.write("NumTasks,NumAnnotations,TotalResources,TaskExtractionMean,TaskExtractionSD,MaterialParsingMean,MaterialParsingSD,SankeyTransformMean,SankeyTransformSD,TotalLatencyMean,TotalLatencySD\n")
            results.forEach { r ->
                writer.write("${r.numTasks},${r.numAnnotations},${r.totalResources},")
                writer.write("${df.format(r.taskExtractionMean)},${df.format(r.taskExtractionSd)},")
                writer.write("${df.format(r.materialParsingMean)},${df.format(r.materialParsingSd)},")
                writer.write("${df.format(r.sankeyTransformMean)},${df.format(r.sankeyTransformSd)},")
                writer.write("${df.format(r.totalLatencyMean)},${df.format(r.totalLatencySd)}\n")
            }
        }
        println(">>> Scalability results written to: $csvPath")
    }
}