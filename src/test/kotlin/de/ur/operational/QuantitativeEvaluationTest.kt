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
    fun `process model has 4 tasks`() {
        val bpmnPath = "src/main/resources/processes/MaterialFlow.bpmn"
        
        val startTime = System.nanoTime()
        val result = bpmnProcessor.loadTaskOrder(bpmnPath)
        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
        
        assertEquals(4, result.size, "MaterialFlow process should contain 4 executable tasks")
        
        println(">>> Task extraction: ${String.format("%.2f", durationMs)} ms")
    }

    @Test
    fun `material annotations parse requirements across all tasks`() {
        val bpmnPath = "src/main/resources/processes/MaterialFlow.bpmn"
        
        val startTime = System.nanoTime()
        val result = materialService.extractMaterialRequirements(bpmnPath)
        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
        
        val totalRequirements = result.sumOf { it.requirements.size }
        
        assertTrue(
            totalRequirements >= 6,
            "Expected at least 6 material requirements across 3 annotated tasks, found $totalRequirements"
        )
        
        assertEquals(3, result.size, "Should have 3 tasks with material annotations")
        
        println(">>> Material parsing: ${String.format("%.2f", durationMs)} ms, $totalRequirements requirements found")
    }

    @Test
    fun `transformation completes in under 50ms`() {
        val bpmnPath = "src/main/resources/processes/MaterialFlow.bpmn"
        
        val startTime = System.nanoTime()
        sankeyService.generateSankeyData(bpmnPath)
        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
        
        assertTrue(
            durationMs < 50.0,
            "Transformation should complete in <50ms, took ${String.format("%.2f", durationMs)}ms"
        )
        
        println(">>> Sankey transformation: ${String.format("%.2f", durationMs)} ms")
    }

    @Test
    fun `sankey generation produces valid structure`() {
        val bpmnPath = "src/main/resources/processes/MaterialFlow.bpmn"
        
        val startTime = System.nanoTime()
        val result = sankeyService.generateSankeyData(bpmnPath)
        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
        
        assertTrue(result.nodes.isNotEmpty(), "Should produce nodes")
        assertTrue(result.links.isNotEmpty(), "Should produce links")
        
        println(">>> Full pipeline: ${String.format("%.2f", durationMs)} ms (${result.nodes.size} nodes, ${result.links.size} links)")
    }
}
