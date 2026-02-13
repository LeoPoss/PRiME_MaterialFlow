package de.ur.operational

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MaterialServiceTest {

    private val materialService = MaterialService()

    @Test
    fun `extractMaterialRequirements parses YAML from MaterialFlow bpmn`() {
        val bpmnPath = "src/main/resources/processes/MaterialFlow.bpmn"

        val result = materialService.extractMaterialRequirements(bpmnPath)

        assertFalse(result.isEmpty())

        val taskWithScrews = result.find { it.taskId == "mountWoodenSlatsToTableTop" }!!
        
        val screwRequirement = taskWithScrews.requirements.find { it.materialName == "Screw M8" }!!
        assertEquals("Screws", screwRequirement.materialType)
        assertEquals(8.0, screwRequirement.requiredQuantity)
        assertEquals("M8-001", screwRequirement.materialID)
    }

    @Test
    fun `extractMaterialRequirements returns empty list for nonexistent file`() {
        val result = materialService.extractMaterialRequirements("/nonexistent/path.bpmn")
        
        assertTrue(result.isEmpty())
    }

    @Test
    fun `extractMaterialRequirements extracts intermediate materials`() {
        val bpmnPath = "src/main/resources/processes/MaterialFlow.bpmn"

        val result = materialService.extractMaterialRequirements(bpmnPath)

        val taskWithIntermediate = result.find { it.taskId == "insertingCornerConnectors" }!!

        val intermediate = taskWithIntermediate.requirements.find { it.materialType == "Intermediate" }!!
        assertEquals("Partially Assembled Table", intermediate.materialName)
    }
}
