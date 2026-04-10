package de.ur.operational.verification.report

import kotlinx.serialization.Serializable

@Serializable
data class VerificationReport(
    val timestamp: String,
    val totalViolations: Int,
    val violations: List<Violation>,
    val summary: Summary,
    val alloyResults: AlloyResults? = null
)

@Serializable
data class AlloyResults(
    val l1Holds: Boolean?,
    val l5Holds: Boolean?,
    val l1Counterexample: String?,
    val l5Counterexample: String?
)

@Serializable
data class Violation(
    val ruleId: String,
    val severity: Severity,
    val elementId: String,
    val location: String,
    val message: String,
    val stage: Int,
    val taskId: String? = null
)

@Serializable
enum class Severity {
    ERROR,
    WARNING
}

@Serializable
data class Summary(
    // Optional human-readable summary/details
    val details: String? = null
)
