package de.ur.operational.verification

import de.ur.operational.MaterialService
import de.ur.operational.model.TaskMaterialRequirements
import de.ur.operational.verification.model.PRiMEAnnotation
import de.ur.operational.verification.model.Requirements
import de.ur.operational.verification.model.MaterialEntry
import de.ur.operational.verification.model.ToolEntry
import de.ur.operational.verification.report.VerificationReport
import de.ur.operational.verification.report.Violation
import de.ur.operational.verification.report.Severity
import de.ur.operational.verification.report.Summary
import de.ur.operational.verification.report.AlloyResults
import java.io.File

data class Stage1Result(
    val taskRequirements: List<TaskMaterialRequirements>
)

data class Stage2Result(
    val annotations: List<PRiMEAnnotation>,
    val taskIds: List<String>
)

data class Stage3Result(
    val report: VerificationReport
)

data class Stage4Result(
    val alloyGenerated: Boolean,
    val alloyModel: String,
    val message: String,
    val alloyResults: AlloyResults? = null
)

class VerificationPipeline(
    private val materialService: MaterialService,
    private val verificationService: VerificationService
) {

  private fun extractStage1(bpmnXml: String): Stage1Result {
    val tempFile = File.createTempFile("temp_bpmn", ".bpmn")
    tempFile.writeText(bpmnXml)
    return try {
      val taskRequirements = materialService.extractMaterialRequirements(tempFile.absolutePath)
      Stage1Result(taskRequirements)
    } finally {
      tempFile.delete()
    }
  }

  private fun parseYamlStage(stage1: Stage1Result): Stage2Result {
    val annotations = stage1.taskRequirements.map { taskReq ->
      val materials = taskReq.requirements.map { req ->
        MaterialEntry(
          materialType = req.resourceType ?: "Unknown",
          materialName = req.resourceName ?: "",
          materialID = req.resourceID ?: "",
          requiredQuantity = req.requiredQuantity,
          unitOfMeasurement = req.unitOfMeasurement ?: ""
        )
      }
      val tools = materials.filter { it.materialType == "Tool" }
      val filteredMaterials = materials.filter { it.materialType != "Tool" }
      
      PRiMEAnnotation(
        requirements = Requirements(
          tools = tools.map { t -> ToolEntry(toolType = t.materialName, brand = "", model = "") },
          materials = filteredMaterials
        )
      )
    }
    val taskIds = stage1.taskRequirements.map { it.taskId }
    return Stage2Result(annotations, taskIds)
  }

  private fun stage3Verify(stage2: Stage2Result): Stage3Result {
    val report = verificationService.verifyAll(stage2.annotations, stage2.taskIds)
    return Stage3Result(report)
  }

  private fun stage4Lifecycle(stage2: Stage2Result, testName: String = "default"): Stage4Result {
    return try {
      val alloyResult = de.ur.operational.verification.alloy.AlloyRunner.runAlloy(
        stage2.annotations,
        stage2.taskIds,
        testName = testName
      )
      
      val message = buildString {
        append("Alloy lifecycle analysis: ")
        if (alloyResult.l1Holds == null) {
          append(" Alloy JAR not found - model written to build/reports/verification/alloy_model.als")
        } else {
          append("L1=${if (alloyResult.l1Holds) "PASS" else "FAIL"}, ")
          append("L5=${if (alloyResult.l5Holds == true) "PASS" else "FAIL"}")
        }
      }
      
      val alloyResults = AlloyResults(
        l1Holds = alloyResult.l1Holds,
        l5Holds = alloyResult.l5Holds,
        l1Counterexample = alloyResult.l1Counterexample,
        l5Counterexample = alloyResult.l5Counterexample
      )
      
      Stage4Result(alloyGenerated = true, alloyModel = alloyResult.rawOutput, message = message, alloyResults = alloyResults)
    } catch (e: Exception) {
      Stage4Result(alloyGenerated = false, alloyModel = "", message = "Alloy failed: ${e.message}", alloyResults = null)
    }
  }

  private fun stage5Report(stage3: Stage3Result, stage4: Stage4Result? = null): VerificationReport {
    val baseViolations = stage3.report.violations.toMutableList()
    val alloyResults = stage4?.alloyResults
    
    // Merge Alloy results as Violations (only if failed)
    if (alloyResults != null) {
      if (alloyResults.l1Holds == false) {
        baseViolations.add(Violation(
          ruleId = "L1",
          severity = Severity.ERROR,
          elementId = "Alloy/Lifecycle",
          location = "Alloy",
          message = "L1 (depletion) violation: Consumable inventory would go negative",
          stage = 4,
          taskId = alloyResults.l1Counterexample ?: "No counterexample"
        ))
      }
      if (alloyResults.l5Holds == false) {
        baseViolations.add(Violation(
          ruleId = "L5",
          severity = Severity.ERROR,
          elementId = "Alloy/Lifecycle",
          location = "Alloy",
          message = "L5 (parallel branch) violation: Exclusive tool used across parallel branches without serialization",
          stage = 4,
          taskId = alloyResults.l5Counterexample ?: "No counterexample"
        ))
      }
    }
    
    return VerificationReport(
      timestamp = stage3.report.timestamp,
      totalViolations = baseViolations.size,
      violations = baseViolations,
      summary = Summary(
        details = if (baseViolations.isEmpty()) "All PRiME invariants passed." else "${baseViolations.size} invariant violations detected."
      ),
      alloyResults = alloyResults
    )
  }

  fun runFromBpmnXml(bpmnXml: String, testName: String = "default"): VerificationReport {
    val stage1 = extractStage1(bpmnXml)
    val stage2 = parseYamlStage(stage1)
    val stage3 = stage3Verify(stage2)
    val stage4 = stage4Lifecycle(stage2, testName)
    return stage5Report(stage3, stage4)
  }
}
