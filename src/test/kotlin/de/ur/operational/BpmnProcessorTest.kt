package de.ur.operational

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BpmnProcessorTest {

    private val bpmnProcessor = BpmnProcessor()

    @Test
    fun `loadTaskOrder extracts correct task order from MaterialFlow bpmn`() {
        val bpmnPath = "src/main/resources/processes/MaterialFlow.bpmn"

        val result = bpmnProcessor.loadTaskOrder(bpmnPath)

        assertEquals(4, result.size)
        assertEquals("createResourceList", result[0])
        assertEquals("mountWoodenSlatsToTableTop", result[1])
        assertEquals("insertingCornerConnectors", result[2])
        assertEquals("attachingTableLegs", result[3])
    }

    @Test
    fun `loadTaskOrder throws for nonexistent file`() {
        assertThrows<IllegalArgumentException> {
            bpmnProcessor.loadTaskOrder("/nonexistent/path.bpmn")
        }
    }
}
