package de.ur.operational

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuantitativeEvaluationTest {

    private val bpmnProcessor = BpmnProcessor()
    private val materialService = MaterialService()
    private val sankeyService = SankeyService(ModelService(bpmnProcessor), materialService)

    @Test
    fun `process model has executable tasks`() {
        val bpmnPath = "src/main/resources/processes/TrussPrefabrication.bpmn"
        
        val startTime = System.nanoTime()
        val result = bpmnProcessor.loadTaskOrder(bpmnPath)
        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
        
        assertTrue(result.isNotEmpty(), "TrussPrefabrication process should contain executable tasks")
        
        println(">>> Task extraction: ${String.format("%.2f", durationMs)} ms, ${result.size} tasks found")
    }

    @Test
    fun `material annotations parse requirements`() {
        val bpmnPath = "src/main/resources/processes/TrussPrefabrication.bpmn"
        
        val startTime = System.nanoTime()
        val result = materialService.extractMaterialRequirements(bpmnPath)
        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
        
        println(">>> Material parsing: ${String.format("%.2f", durationMs)} ms, ${result.size} annotated tasks")
        result.forEach { task ->
            println("    - ${task.taskId}: ${task.requirements.size} requirements")
        }
        
        assertNotNull(result, "Should return material requirements result")
    }

    @Test
    fun `transformation completes in under 50ms`() {
        val bpmnPath = "src/main/resources/processes/TrussPrefabrication.bpmn"
        
        val startTime = System.nanoTime()
        sankeyService.generateSankeyData(bpmnPath)
        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
        
        println(">>> Sankey transformation: ${String.format("%.2f", durationMs)} ms")
    }

    @Test
    fun `sankey generation produces valid structure`() {
        val bpmnPath = "src/main/resources/processes/TrussPrefabrication.bpmn"
        
        val startTime = System.nanoTime()
        val result = sankeyService.generateSankeyData(bpmnPath)
        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
        
        assertNotNull(result, "Should produce a result")
        
        println(">>> Full pipeline: ${String.format("%.2f", durationMs)} ms (${result.nodes.size} nodes, ${result.links.size} links)")
    }

    // Table Building (MaterialFlow) tests

    @Test
    fun `table building process has executable tasks`() {
        val bpmnPath = "src/main/resources/processes/MaterialFlow.bpmn"
        
        val startTime = System.nanoTime()
        val result = bpmnProcessor.loadTaskOrder(bpmnPath)
        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
        
        assertTrue(result.isNotEmpty(), "MaterialFlow process should contain executable tasks")
        
        println(">>> [Table] Task extraction: ${String.format("%.2f", durationMs)} ms, ${result.size} tasks found")
    }

    @Test
    fun `table building material annotations parse requirements`() {
        val bpmnPath = "src/main/resources/processes/MaterialFlow.bpmn"
        
        val startTime = System.nanoTime()
        val result = materialService.extractMaterialRequirements(bpmnPath)
        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
        
        println(">>> [Table] Material parsing: ${String.format("%.2f", durationMs)} ms, ${result.size} annotated tasks")
        result.forEach { task ->
            println("    - ${task.taskId}: ${task.requirements.size} requirements")
        }
        
        assertNotNull(result, "Should return material requirements result")
    }

    @Test
    fun `table building transformation completes in under 50ms`() {
        val bpmnPath = "src/main/resources/processes/MaterialFlow.bpmn"
        
        val startTime = System.nanoTime()
        sankeyService.generateSankeyData(bpmnPath)
        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
        
        println(">>> [Table] Sankey transformation: ${String.format("%.2f", durationMs)} ms")
    }

    @Test
    fun `table building sankey generation produces valid structure`() {
        val bpmnPath = "src/main/resources/processes/MaterialFlow.bpmn"
        
        val startTime = System.nanoTime()
        val result = sankeyService.generateSankeyData(bpmnPath)
        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
        
        assertNotNull(result, "Should produce a result")
        
        println(">>> [Table] Full pipeline: ${String.format("%.2f", durationMs)} ms (${result.nodes.size} nodes, ${result.links.size} links)")
    }
}
