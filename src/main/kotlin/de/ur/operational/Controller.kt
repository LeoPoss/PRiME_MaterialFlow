package de.ur.operational

import de.ur.operational.model.ProcessDefinition
import de.ur.operational.model.SankeyData
import de.ur.operational.model.TaskMaterialRequirements
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {
    @Bean
    fun webClient(): WebClient = WebClient.builder()
        .baseUrl("http://localhost:8080/engine-rest")
        .build()
}

@RestController
@RequestMapping("/api")
class SankeyController(
    private val modelService: ModelService,
    private val materialService: MaterialService,
    private val sankeyService: SankeyService,
    private val webClient: WebClient
) {

    @GetMapping("/order/{key}")
    fun getTaskOrder(@PathVariable("key") key: String): ResponseEntity<List<String>> {
        val bpmnPath = getBpmnPath(key)
        val result = modelService.loadTaskOrder(bpmnPath)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/sankey/{key}")
    fun getSankeyData(@PathVariable("key") key: String): ResponseEntity<SankeyData> {
        val bpmnPath = getBpmnPath(key)
        val result = sankeyService.generateSankeyData(bpmnPath)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/materials/{key}")
    fun getMaterialRequirements(@PathVariable("key") key: String): ResponseEntity<List<TaskMaterialRequirements>> {
        val bpmnPath = getBpmnPath(key)
        val materials = materialService.extractMaterialRequirements(bpmnPath)
        return ResponseEntity.ok(materials)
    }

    private fun getBpmnPath(key: String): String {
        val processDefinition = webClient.get()
            .uri("/process-definition/key/{key}", key)
            .retrieve()
            .bodyToMono(ProcessDefinition::class.java)
            .block()
            ?: throw IllegalStateException("Process definition not found for key: $key")

        return processDefinition.resource
    }
}


