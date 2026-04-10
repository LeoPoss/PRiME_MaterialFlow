package de.ur.operational.verification

import de.ur.operational.verification.model.PRiMEAnnotation
import de.ur.operational.verification.model.Requirements
import de.ur.operational.verification.model.MaterialEntry
import de.ur.operational.verification.model.ToolEntry
import de.ur.operational.verification.VerificationService
import de.ur.operational.verification.VerificationService.VerificationContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VerificationServiceTest {

    private fun material(name: String, type: String, id: String, qty: Double, unit: String): MaterialEntry {
        return MaterialEntry(
            materialName = name,
            materialType = type,
            materialID = id,
            requiredQuantity = qty,
            unitOfMeasurement = unit
        )
    }

    private fun tool(type: String, brand: String = "Brand", model: String = "Model"): ToolEntry {
        return ToolEntry(toolType = type, brand = brand, model = model)
    }

    private fun annotation(materials: List<MaterialEntry> = emptyList(), tools: List<ToolEntry> = emptyList()): PRiMEAnnotation {
        val req = Requirements(materials = materials, tools = tools)
        return PRiMEAnnotation(requirements = req)
    }

    @Test
    fun wf1_passes_when_attached_to_one_activity() {
        val ann = annotation()
        val viol = VerificationService.wf1(ann, VerificationContext(attachedActivityCount = 1, allAnnotations = listOf(ann)))
        assertNull(viol)
    }

    @Test
    fun wf1_fails_when_attached_to_multiple_activities() {
        val ann = annotation()
        val viol = VerificationService.wf1(ann, VerificationContext(attachedActivityCount = 2, allAnnotations = listOf(ann)))
        assertNotNull(viol)
        assertEquals("WF1", viol!!.ruleId)
    }

    @Test
    fun wf3_passes_when_requirements_present() {
        val m = material("Screw M8","Screws","S-001", 8.0, "pcs")
        val ann = annotation(materials = listOf(m))
        val viol = VerificationService.wf3(ann)
        assertNull(viol)
    }

    @Test
    fun wf3_fails_when_requirements_empty() {
        val ann = annotation()
        val viol = VerificationService.wf3(ann)
        assertNotNull(viol)
        assertEquals("WF3", viol!!.ruleId)
    }

    @Test
    fun wf4_passes_when_materials_have_all_fields() {
        val m = material("Nail","Screws","N-01", 50.0, "pcs")
        val ann = annotation(materials = listOf(m))
        val viol = VerificationService.wf4(ann)
        assertNull(viol)
    }

    @Test
    fun wf4_fails_when_material_missing_fields() {
        // quantity is zero -> violates WF4
        val m = material("Nail","Screws","N-01", 0.0, "pcs")
        val ann = annotation(materials = listOf(m))
        val viol = VerificationService.wf4(ann)
        assertNotNull(viol)
        assertEquals("WF4", viol!!.ruleId)
    }

    @Test
    fun wf5_passes_when_tool_entries_have_types() {
        val t = tool("Drill")
        val ann = annotation(tools = listOf(t))
        val viol = VerificationService.wf5(ann)
        assertNull(viol)
    }

    @Test
    fun wf5_fails_when_tool_entry_missing_type() {
        val t = ToolEntry(toolType = "", brand = "Brand", model = "Model")
        val ann = annotation(tools = listOf(t))
        val viol = VerificationService.wf5(ann)
        assertNotNull(viol)
        assertEquals("WF5", viol!!.ruleId)
    }

    @Test
    fun wf2_always_passes_null() {
        val ann = annotation()
        val viol = VerificationService.wf2(ann)
        assertNull(viol)
    }

    @Test
    fun wf6_fails_when_more_than_one_annotation_per_activity() {
        val ann1 = annotation()
        val ann2 = annotation()
        val viol = VerificationService.wf6(ann1, VerificationContext(attachedActivityCount = 1, allAnnotations = listOf(ann1, ann2)))
        assertNotNull(viol)
        assertEquals("WF6", viol!!.ruleId)
    }

    @Test
    fun c1_fails_on_duplicate_material_entries_within_same_annotation() {
        val m = material("Glue","Adhesive","G-1", 2.0, "kg")
        val ann = annotation(materials = listOf(m, m))
        val viol = VerificationService.c1(ann)
        assertNotNull(viol)
        assertEquals("C1", viol!!.ruleId)
    }

    @Test
    fun c2_fails_on_inconsistent_units_across_annotations() {
        val m1 = material("Copper","Metal","C-1", 5.0, "kg")
        val annA = annotation(materials = listOf(m1))
        val m2 = material("Copper","Metal","C-1", 5.0, "lb")
        val annB = annotation(materials = listOf(m2))
        val viol = VerificationService.c2(annB, VerificationContext(allAnnotations = listOf(annA, annB)))
        assertNotNull(viol)
        assertEquals("C2", viol!!.ruleId)
    }

    @Test
    fun c3_fails_on_duplicate_tool_entries_within_same_annotation() {
        val t = tool("Drill")
        val ann = annotation(tools = listOf(t, t))
        val viol = VerificationService.c3(ann)
        assertNotNull(viol)
        assertEquals("C3", viol!!.ruleId)
    }

    @Test
    fun cp1_nan_quantity_yields_violation() {
        val m = material("Bolt","Fastener","B-1", Double.NaN, "pcs")
        val ann = annotation(materials = listOf(m))
        val viol = VerificationService.cp1(ann)
        assertNotNull(viol)
        assertEquals("CP1", viol!!.ruleId)
    }

    @Test
    fun cp2_missing_identification_yields_violation() {
        val m = MaterialEntry(materialName = "Bolt", materialType = "Fastener", materialID = "", requiredQuantity = 1.0, unitOfMeasurement = "pcs")
        val ann = annotation(materials = listOf(m))
        val viol = VerificationService.cp2(ann)
        assertNotNull(viol)
        assertEquals("CP2", viol!!.ruleId)
    }

    @Test
    fun cp3_missing_tool_type_yields_violation() {
        val t = ToolEntry(toolType = "", brand = "Brand", model = "Model")
        val ann = annotation(tools = listOf(t))
        val viol = VerificationService.cp3(ann)
        assertNotNull(viol)
        assertEquals("CP3", viol!!.ruleId)
    }

    @Test
    fun l2_passes_when_tool_available_after_release() {
        val ann = annotation()
        val viol = VerificationService.l2(ann, VerificationContext(toolAvailabilityAfterRelease = true))
        assertNull(viol)
    }

    @Test
    fun l2_fails_when_tool_unavailable_after_release() {
        val ann = annotation()
        val viol = VerificationService.l2(ann, VerificationContext(toolAvailabilityAfterRelease = false))
        assertNotNull(viol)
        assertEquals("L2", viol!!.ruleId)
    }

    // L3 and L4 are placeholders in the implementation; ensure they return null
    @Test
    fun l3_and_l4_are_noops() {
        val ann = annotation()
        assertNull(VerificationService.l3(ann))
        assertNull(VerificationService.l4(ann))
    }

    @Test
    fun cp4_and_cp5_always_pass_null() {
        val ann = annotation()
        assertNull(VerificationService.cp4(ann))
        assertNull(VerificationService.cp5(ann))
    }

    @Test
    fun c4_and_c6_always_pass_null() {
        val ann = annotation()
        val ctx = VerificationContext(allAnnotations = listOf(ann))
        assertNull(VerificationService.c4(ann, ctx))
        assertNull(VerificationService.c6(ann, ctx))
    }
}
