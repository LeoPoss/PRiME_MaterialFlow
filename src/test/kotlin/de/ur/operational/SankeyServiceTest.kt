package de.ur.operational

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SankeyServiceTest {

    private val sankeyService = SankeyService(ModelService(BpmnProcessor()), MaterialService())

    @Test
    fun `generateSankeyData creates valid sankey structure from MaterialFlow bpmn`() {
        val bpmnPath = "src/main/resources/processes/MaterialFlow.bpmn"

        val result = sankeyService.generateSankeyData(bpmnPath)

        assertNotNull(result)
        assertFalse(result.nodes.isEmpty())
        assertFalse(result.links.isEmpty())
    }

    @Test
    fun `generateSankeyData includes material nodes`() {
        val bpmnPath = "src/main/resources/processes/MaterialFlow.bpmn"

        val result = sankeyService.generateSankeyData(bpmnPath)

        val materialNodes = result.nodes.filter { it.type != "task" && it.type != "endEvent" }
        assertTrue(materialNodes.isNotEmpty())
        
        val screwNode = result.nodes.find { it.name == "Screw M8" }
        assertNotNull(screwNode)
    }

    @Test
    fun `generateSankeyData includes task nodes`() {
        val bpmnPath = "src/main/resources/processes/MaterialFlow.bpmn"

        val result = sankeyService.generateSankeyData(bpmnPath)

        val taskNodes = result.nodes.filter { it.type == "task" }
        assertTrue(taskNodes.isNotEmpty())
    }
}
