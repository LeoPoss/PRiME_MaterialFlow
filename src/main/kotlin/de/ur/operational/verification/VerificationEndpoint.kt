package de.ur.operational.verification

import de.ur.operational.verification.report.VerificationReport
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import java.nio.file.Files
import java.nio.file.Paths

@RestController
@RequestMapping("/api/verify")
class VerificationEndpoint(private val verificationPipeline: VerificationPipeline) {

    data class VerificationRequest(
        val bpmnXml: String? = null,
        val bpmnFilePath: String? = null
    )

    @PostMapping("/trigger")
    fun trigger(@RequestBody request: VerificationRequest): ResponseEntity<VerificationReport> {
        val xmlContent: String = request.bpmnXml ?: run {
            val path = request.bpmnFilePath
                ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
            try {
                Files.readString(Paths.get(path))
            } catch (e: Exception) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
            }
        }

        val report = verificationPipeline.runFromBpmnXml(xmlContent)
        return ResponseEntity.ok(report)
    }
}
