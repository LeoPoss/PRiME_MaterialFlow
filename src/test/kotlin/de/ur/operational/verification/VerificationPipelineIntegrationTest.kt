package de.ur.operational.verification

import de.ur.operational.verification.report.VerificationReport
import de.ur.operational.verification.report.Summary
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class VerificationPipelineIntegrationTest {

    private fun loadResourceAsString(path: String): String {
        val cls = this::class.java
        val stream = cls.getResourceAsStream("/" + path.trimStart('/'))
            ?: throw IllegalArgumentException("Resource not found: $path")
        return stream.readBytes().toString(StandardCharsets.UTF_8)
    }

    private fun runVerification(bpmnPath: String, testName: String = "default"): VerificationReport {
        val bpmnXml = loadResourceAsString(bpmnPath)
        val materialService = de.ur.operational.MaterialService()
        val pipeline = VerificationPipeline(materialService, VerificationService)
        return pipeline.runFromBpmnXml(bpmnXml, testName)
    }

    private fun writeReport(report: VerificationReport, name: String) {
        val json = Json { prettyPrint = true }
        val jsonString = json.encodeToString(report)
        val outputDir = Paths.get("build/reports/verification")
        Files.createDirectories(outputDir)
        val outputPath = outputDir.resolve(name)
        Files.writeString(outputPath, jsonString)
        println("Report written to: $outputPath")
    }

    @Test
    fun `valid BPMN should pass all rules`() {
        val report = runVerification("processes/MaterialFlowYAML.bpmn")
        writeReport(report, "valid_materialflow.json")
        assertEquals(0, report.totalViolations, "Valid BPMN should have no violations")
    }

    @Test
    fun `invalid WF3 empty requirements should fail`() {
        val report = runVerification("processes/invalid_wf3_empty_requirements.bpmn")
        writeReport(report, "invalid_wf3.json")
        assertTrue(report.violations.any { it.ruleId == "WF3" }, "Should detect WF3 violation")
    }

    @Test
    fun `invalid WF4 C5 negative quantity should fail`() {
        val report = runVerification("processes/invalid_wf4_c5_negative_quantity.bpmn")
        writeReport(report, "invalid_wf4_c5.json")
        assertTrue(report.violations.any { it.ruleId == "WF4" }, "Should detect WF4 violation")
        assertTrue(report.violations.any { it.ruleId == "C5" }, "Should detect C5 violation")
    }

    @Test
    fun `invalid CP2 missing material ID should fail`() {
        val report = runVerification("processes/invalid_cp2_missing_id.bpmn")
        writeReport(report, "invalid_cp2.json")
        assertTrue(report.violations.any { it.ruleId == "CP2" }, "Should detect CP2 violation")
    }

    @Test
    fun `invalid C1 duplicate materials should fail`() {
        val report = runVerification("processes/invalid_c1_duplicate_materials.bpmn")
        writeReport(report, "invalid_c1.json")
        assertTrue(report.violations.any { it.ruleId == "C1" }, "Should detect C1 violation")
    }

    @Test
    fun `integration test produces valid JSON report`() {
        val bpmnXml = loadResourceAsString("processes/MaterialFlowYAML.bpmn")
        val materialService = de.ur.operational.MaterialService()
        val pipeline = VerificationPipeline(materialService, VerificationService)

        val report: VerificationReport = pipeline.runFromBpmnXml(bpmnXml)
        writeReport(report, "integration_test_report.json")

        val json = Json { prettyPrint = true }
        val jsonString = json.encodeToString(report)

        assertTrue(jsonString.contains("\"timestamp\""), "JSON should contain timestamp")
        assertTrue(jsonString.contains("\"totalViolations\":"), "JSON should contain totalViolations")
        assertTrue(jsonString.contains("\"violations\":"), "JSON should contain violations array")
        assertTrue(jsonString.contains("\"summary\""), "JSON should contain summary")
    }

    @Test
    fun `valid BPMN passes L1 and L5`() {
        val report = runVerification("processes/MaterialFlowYAML.bpmn", "valid")
        writeReport(report, "valid_alloy.json")
        assertTrue(report.alloyResults != null, "Should have alloy results")
        assertTrue(report.alloyResults!!.l1Holds == true, "L1 should pass for valid process")
        assertTrue(report.alloyResults!!.l5Holds == true, "L5 should pass for valid process")
    }

    @Test
    fun `L1 violation test detects depletion`() {
        val report = runVerification("processes/l1_depletion_test.bpmn", "l1_violation")
        writeReport(report, "l1_violation.json")
        assertTrue(report.alloyResults != null, "Should have alloy results")
        assertTrue(report.alloyResults!!.l1Holds == false, "L1 should FAIL (depletion violation)")
    }

    @Test
    fun `L5 violation test detects parallel tool`() {
        val report = runVerification("processes/l5_parallel_tool_test.bpmn", "l5_violation")
        writeReport(report, "l5_violation.json")
        assertTrue(report.alloyResults != null, "Should have alloy results")
        assertTrue(report.alloyResults!!.l5Holds == false, "L5 should FAIL (parallel tool violation)")
    }
}
