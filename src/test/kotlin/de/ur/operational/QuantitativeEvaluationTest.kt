package de.ur.operational

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.io.FileWriter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.sqrt

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuantitativeEvaluationTest {

    private val bpmnProcessor = BpmnProcessor()
    private val materialService = MaterialService()
    private val sankeyService = SankeyService(ModelService(bpmnProcessor), materialService)
    
    // Use US locale to ensure dots instead of commas in decimals
    private val df = DecimalFormat("#0.00", DecimalFormatSymbols.getInstance(Locale.US))
    
    private val ITERATIONS = 10
    private val WARMUP_ITERATIONS = 5

    data class EvaluationResult(
        val operation: String,
        val processTasks: Int,
        val annotatedTasks: Int,
        val resourceRequirements: Int,
        val annotationFormat: String,
        val taskOrderExtractionMs: Double,
        val materialAnnotationParsingMs: Double,
        val sankeyTransformationMs: Double,
        val totalPipelineLatencyMs: Double
    )

    data class AggregatedResult(
        val operation: String,
        val processTasks: Int,
        val annotatedTasks: Int,
        val resourceRequirements: Int,
        val annotationFormat: String,
        val taskOrderExtractionMean: Double,
        val taskOrderExtractionSd: Double,
        val materialAnnotationParsingMean: Double,
        val materialAnnotationParsingSd: Double,
        val sankeyTransformationMean: Double,
        val sankeyTransformationSd: Double,
        val totalPipelineLatencyMean: Double,
        val totalPipelineLatencySd: Double
    )

    private fun detectAnnotationFormat(bpmnPath: String): String {
        val content = File(bpmnPath).readText()
        // Check for YAML indicators (indentation-based, dash lists) vs JSON (curly braces)
        val yamlPattern = Regex("""resourceRequirements:\s*\n\s+- """)
        return if (yamlPattern.containsMatchIn(content)) "YAML" else "JSON"
    }

    private fun runEvaluation(operation: String, bpmnPath: String): EvaluationResult {
        // Task order extraction
        val taskStartTime = System.nanoTime()
        val taskOrder = bpmnProcessor.loadTaskOrder(bpmnPath)
        val taskDurationMs = (System.nanoTime() - taskStartTime) / 1_000_000.0

        // Material annotation parsing
        val materialStartTime = System.nanoTime()
        val materialRequirements = materialService.extractMaterialRequirements(bpmnPath)
        val materialDurationMs = (System.nanoTime() - materialStartTime) / 1_000_000.0

        // Total resource requirements count
        val totalRequirements = materialRequirements.sumOf { it.requirements.size }

        // Sankey transformation (full pipeline)
        val sankeyStartTime = System.nanoTime()
        val sankeyResult = sankeyService.generateSankeyData(bpmnPath)
        val sankeyDurationMs = (System.nanoTime() - sankeyStartTime) / 1_000_000.0

        // Total pipeline latency
        val totalLatencyMs = taskDurationMs + materialDurationMs + sankeyDurationMs

        val format = detectAnnotationFormat(bpmnPath)

        return EvaluationResult(
            operation = operation,
            processTasks = taskOrder.size,
            annotatedTasks = materialRequirements.size,
            resourceRequirements = totalRequirements,
            annotationFormat = format,
            taskOrderExtractionMs = taskDurationMs,
            materialAnnotationParsingMs = materialDurationMs,
            sankeyTransformationMs = sankeyDurationMs,
            totalPipelineLatencyMs = totalLatencyMs
        )
    }

    private fun runAggregatedEvaluation(operation: String, bpmnPath: String): AggregatedResult {
        // WARM-UP PHASE (not measured)
        // This forces class loading, Jackson/YAML initialization, and JIT compilation
        repeat(WARMUP_ITERATIONS) {
            runEvaluation(operation, bpmnPath)
        }
        
        // MEASUREMENT PHASE
        val results = mutableListOf<EvaluationResult>()
        repeat(ITERATIONS) {
            results.add(runEvaluation(operation, bpmnPath))
        }
        
        // Calculate mean and SD for each metric
        val taskTimes = results.map { it.taskOrderExtractionMs }
        val materialTimes = results.map { it.materialAnnotationParsingMs }
        val sankeyTimes = results.map { it.sankeyTransformationMs }
        val totalTimes = results.map { it.totalPipelineLatencyMs }
        
        return AggregatedResult(
            operation = operation,
            processTasks = results.first().processTasks,
            annotatedTasks = results.first().annotatedTasks,
            resourceRequirements = results.first().resourceRequirements,
            annotationFormat = results.first().annotationFormat,
            taskOrderExtractionMean = mean(taskTimes),
            taskOrderExtractionSd = sd(taskTimes),
            materialAnnotationParsingMean = mean(materialTimes),
            materialAnnotationParsingSd = sd(materialTimes),
            sankeyTransformationMean = mean(sankeyTimes),
            sankeyTransformationSd = sd(sankeyTimes),
            totalPipelineLatencyMean = mean(totalTimes),
            totalPipelineLatencySd = sd(totalTimes)
        )
    }
    
    private fun mean(values: List<Double>): Double = values.sum() / values.size
    
    private fun sd(values: List<Double>): Double {
        val avg = mean(values)
        val variance = values.map { (it - avg) * (it - avg) }.sum() / values.size
        return sqrt(variance)
    }

    private fun writeAggregatedResultsToCsv(results: List<AggregatedResult>, csvPath: String) {
        FileWriter(csvPath).use { writer ->
            // Header
            writer.write("Operation,Process Tasks,Annotated Tasks,Resource Requirements,Annotation Format,")
            writer.write("Task Order Extraction (ms), Material Annotation Parsing (ms),Sankey Transformation (ms),Total Pipeline Latency Mean (ms)\n")

            // Data rows
            results.forEach { result ->
                writer.write("${result.operation},${result.processTasks},${result.annotatedTasks},${result.resourceRequirements},${result.annotationFormat},")
                writer.write("${df.format(result.taskOrderExtractionMean)} ± ${df.format(result.taskOrderExtractionSd)},")
                writer.write("${df.format(result.materialAnnotationParsingMean)} ± ${df.format(result.materialAnnotationParsingSd)},")
                writer.write("${df.format(result.sankeyTransformationMean)} ± ${df.format(result.sankeyTransformationSd)},")
                writer.write("${df.format(result.totalPipelineLatencyMean)} ± ${df.format(result.totalPipelineLatencySd)}\n")
            }
        }
    }

    @Test
    fun `quantitative evaluation runs 10 times and reports mean with sd`() {
        val results = mutableListOf<AggregatedResult>()

        // Truss Prefabrication
        val trussResult = runAggregatedEvaluation("Truss Prefabrication", "src/main/resources/processes/TrussPrefabrication.bpmn")
        results.add(trussResult)
        println(">>> Truss Prefabrication: ${trussResult.processTasks} tasks, ${trussResult.annotatedTasks} annotated, ${trussResult.resourceRequirements} requirements, ${trussResult.annotationFormat}")
        println("    Task extraction: ${df.format(trussResult.taskOrderExtractionMean)} ± ${df.format(trussResult.taskOrderExtractionSd)} ms")
        println("    Material parsing: ${df.format(trussResult.materialAnnotationParsingMean)} ± ${df.format(trussResult.materialAnnotationParsingSd)} ms")
        println("    Sankey transformation: ${df.format(trussResult.sankeyTransformationMean)} ± ${df.format(trussResult.sankeyTransformationSd)} ms")
        println("    Total: ${df.format(trussResult.totalPipelineLatencyMean)} ± ${df.format(trussResult.totalPipelineLatencySd)} ms")

        // Table Building (MaterialFlow)
        val tableResult = runAggregatedEvaluation("Table Building", "src/main/resources/processes/MaterialFlow.bpmn")
        results.add(tableResult)
        println(">>> Table Building: ${tableResult.processTasks} tasks, ${tableResult.annotatedTasks} annotated, ${tableResult.resourceRequirements} requirements, ${tableResult.annotationFormat}")
        println("    Task extraction: ${df.format(tableResult.taskOrderExtractionMean)} ± ${df.format(tableResult.taskOrderExtractionSd)} ms")
        println("    Material parsing: ${df.format(tableResult.materialAnnotationParsingMean)} ± ${df.format(tableResult.materialAnnotationParsingSd)} ms")
        println("    Sankey transformation: ${df.format(tableResult.sankeyTransformationMean)} ± ${df.format(tableResult.sankeyTransformationSd)} ms")
        println("    Total: ${df.format(tableResult.totalPipelineLatencyMean)} ± ${df.format(tableResult.totalPipelineLatencySd)} ms")

        // Synthetic Scaling (TrussPrefabricationLong)
        val scalingResult = runAggregatedEvaluation("Synthetic Scaling", "src/main/resources/processes/TrussPrefabricationLong.bpmn")
        results.add(scalingResult)
        println(">>> Synthetic Scaling: ${scalingResult.processTasks} tasks, ${scalingResult.annotatedTasks} annotated, ${scalingResult.resourceRequirements} requirements, ${scalingResult.annotationFormat}")
        println("    Task extraction: ${df.format(scalingResult.taskOrderExtractionMean)} ± ${df.format(scalingResult.taskOrderExtractionSd)} ms")
        println("    Material parsing: ${df.format(scalingResult.materialAnnotationParsingMean)} ± ${df.format(scalingResult.materialAnnotationParsingSd)} ms")
        println("    Sankey transformation: ${df.format(scalingResult.sankeyTransformationMean)} ± ${df.format(scalingResult.sankeyTransformationSd)} ms")
        println("    Total: ${df.format(scalingResult.totalPipelineLatencyMean)} ± ${df.format(scalingResult.totalPipelineLatencySd)} ms")

        // Write to CSV
        val csvPath = "build/reports/quantitative-evaluation.csv"
        File("build/reports").mkdirs()
        writeAggregatedResultsToCsv(results, csvPath)
        println(">>> Results written to: $csvPath")

        // Assertions
        assertTrue(results.isNotEmpty(), "Should have evaluation results")
        assertTrue(File(csvPath).exists(), "CSV file should be created")
    }
}
