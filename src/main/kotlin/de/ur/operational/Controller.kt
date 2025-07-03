package de.ur.operational

import de.ur.operational.model.SankeyData
import de.ur.operational.model.TaskMaterialRequirements
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient

data class ProcessDefinition(
    val id: String,
    val key: String,
    val resource: String,
)

val webClient: WebClient = WebClient.builder().baseUrl("http://localhost:8080/engine-rest").build()

@RestController
@RequestMapping("/api")
class SankeyController(
    private val modelService: ModelService,
    private val materialService: MaterialService,
    private val sankeyService: SankeyService
) {

    @GetMapping("/order/{key}")
    fun getTaskOrder(@PathVariable("key") key: String): ResponseEntity<List<String>> {
        val result = modelService.loadTaskOrder(getBPMNPath(key))
        return ResponseEntity.ok(result)
    }

    @GetMapping("/sankey/{key}")
    fun getSankeyData(@PathVariable("key") key: String): ResponseEntity<SankeyData> {
        val result = sankeyService.generateSankeyData(getBPMNPath(key))
        return ResponseEntity.ok(result)
    }

    @GetMapping("/materials/{key}")
    fun getMaterialRequirements(@PathVariable("key") key: String): ResponseEntity<List<TaskMaterialRequirements>> {
        val materials = materialService.extractMaterialRequirements(getBPMNPath(key))
        return ResponseEntity.ok(materials)
    }

    fun getBPMNPath(key: String): String {
        val processDefinition = webClient.get().uri("/process-definition/key/{key}", key).retrieve()
            .bodyToMono(ProcessDefinition::class.java).block()

        return processDefinition!!.resource
    }
}


