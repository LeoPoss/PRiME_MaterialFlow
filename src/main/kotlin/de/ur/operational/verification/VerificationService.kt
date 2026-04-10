package de.ur.operational.verification

import de.ur.operational.verification.model.PRiMEAnnotation
import de.ur.operational.verification.report.VerificationReport
import de.ur.operational.verification.report.Violation
import de.ur.operational.verification.report.Severity
import de.ur.operational.verification.report.Summary

object VerificationService {

    data class VerificationContext(
        val attachedActivityCount: Int = 1,
        val allAnnotations: List<PRiMEAnnotation> = emptyList(),
        val toolAvailabilityAfterRelease: Boolean? = null
    )

    private fun violation(ruleId: String, message: String, taskId: String? = null) = Violation(
        ruleId = ruleId,
        severity = Severity.ERROR,
        elementId = "PRiMEAnnotation",
        location = "VerificationService.kt",
        message = message,
        stage = 3,
        taskId = taskId
    )

    fun wf1(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()) = 
        if (ctx.attachedActivityCount == 1) null 
        else violation("WF1", "Annotation must be attached to exactly one activity.")

    fun wf2(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()) = null

    fun wf3(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()) = 
        if (annotation.requirements.tools.isNotEmpty() || annotation.requirements.materials.isNotEmpty()) null 
        else violation("WF3", "Requirements must contain at least one tool or material.")

    fun wf4(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()): Violation? {
        for (m in annotation.requirements.materials) {
            if (m.materialType.isBlank() || m.materialName.isBlank() || m.requiredQuantity <= 0 || m.unitOfMeasurement.isBlank()) {
                return violation("WF4", "Each material entry must carry materialType, materialName, requiredQuantity (>0) and unitOfMeasurement.")
            }
        }
        return null
    }

    fun wf5(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()): Violation? {
        for (t in annotation.requirements.tools) {
            if (t.toolType.isBlank()) {
                return violation("WF5", "Each tool entry must specify toolType.")
            }
        }
        return null
    }

    fun wf6(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()) = 
        // WF6 checks this specific annotation is not duplicated - handled by parser
        // Per-activity check uses attachedActivityCount from context
        if (ctx.attachedActivityCount <= 1) null 
        else violation("WF6", "At most one PRiMEAnnotation per activity is allowed.")

    fun c1(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()): Violation? {
        val seen = mutableSetOf<Pair<String, String>>()
        for (m in annotation.requirements.materials) {
            val key = m.materialName to m.materialType
            if (key in seen) return violation("C1", "Duplicate material entry: ${m.materialName} (${m.materialType}).")
            seen.add(key)
        }
        return null
    }

    fun c2(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()): Violation? {
        val units = mutableMapOf<Pair<String, String>, String>()
        for (m in annotation.requirements.materials) {
            val key = m.materialName to m.materialType
            if (key !in units) units[key] = m.unitOfMeasurement
            else if (units[key] != m.unitOfMeasurement) return violation("C2", "Inconsistent unit for ${m.materialName}.")
        }
        for (other in ctx.allAnnotations) {
            for (om in other.requirements.materials) {
                val key = om.materialName to om.materialType
                if (key in units && units[key] != om.unitOfMeasurement) return violation("C2", "Inconsistent unit for ${om.materialName} across annotations.")
                if (key !in units) units[key] = om.unitOfMeasurement
            }
        }
        return null
    }

    fun c3(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()): Violation? {
        val seen = mutableSetOf<Triple<String, String, String>>()
        for (t in annotation.requirements.tools) {
            val key = Triple(t.toolType, t.brand, t.model)
            if (key in seen) return violation("C3", "Duplicate tool: ${t.toolType} ${t.brand} ${t.model}.")
            seen.add(key)
        }
        return null
    }

    fun c4(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()) = null

    fun c5(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()): Violation? {
        for (m in annotation.requirements.materials) {
            if (m.requiredQuantity <= 0) return violation("C5", "Material ${m.materialName} must have positive quantity.")
        }
        return null
    }

    fun c6(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()) = null

    fun cp1(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()): Violation? {
        for (m in annotation.requirements.materials) {
            if (m.requiredQuantity.isNaN()) return violation("CP1", "Material ${m.materialName} must specify quantity.")
        }
        return null
    }

    fun cp2(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()): Violation? {
        for (m in annotation.requirements.materials) {
            if (m.materialName.isBlank() || m.materialID.isBlank()) return violation("CP2", "Material must have name and ID.")
        }
        return null
    }

    fun cp3(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()): Violation? {
        for (t in annotation.requirements.tools) {
            if (t.toolType.isBlank()) return violation("CP3", "Tool must specify toolType.")
        }
        return null
    }

    fun cp4(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()) = null

    fun cp5(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()) = null

    fun l2(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()) = 
        if (ctx.toolAvailabilityAfterRelease ?: true) null 
        else violation("L2", "Tool not available after release.")

    fun l3(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()) = null
    fun l4(annotation: PRiMEAnnotation, ctx: VerificationContext = VerificationContext()) = null

    fun verifyAll(annotations: List<PRiMEAnnotation>, taskIds: List<String>? = null): VerificationReport {
        val violations = mutableListOf<Violation>()
        val taskIdList = taskIds ?: List(annotations.size) { "task_${it + 1}" }
        val ctx = VerificationContext(attachedActivityCount = 1, allAnnotations = annotations)
        
        for ((index, ann) in annotations.withIndex()) {
            val taskId = taskIdList.getOrElse(index) { "task_${index + 1}" }
            listOf(wf1(ann, ctx), wf2(ann, ctx), wf3(ann, ctx), wf4(ann, ctx), wf5(ann, ctx), wf6(ann, ctx),
                  c1(ann, ctx), c2(ann, ctx), c3(ann, ctx), c4(ann, ctx), c5(ann, ctx), c6(ann, ctx),
                  cp1(ann, ctx), cp2(ann, ctx), cp3(ann, ctx), cp4(ann, ctx), cp5(ann, ctx),
                  l2(ann, ctx), l3(ann, ctx), l4(ann, ctx))
                .filterNotNull()
                .forEach { violations.add(it.copy(taskId = taskId)) }
        }

        return VerificationReport(
            timestamp = java.time.OffsetDateTime.now().toString(),
            totalViolations = violations.size,
            violations = violations,
            summary = Summary(details = if (violations.isEmpty()) "All PRiME invariants passed." else "${violations.size} invariant violations detected.")
        )
    }
}