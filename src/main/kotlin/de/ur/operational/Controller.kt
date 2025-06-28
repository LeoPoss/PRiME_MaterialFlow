package de.ur.operational

import de.ur.operational.model.SankeyData
import de.ur.operational.model.TaskMaterialRequirements
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class SankeyController(
    private val modelService: ModelService,
    private val materialService: MaterialService,
    private val sankeyService: SankeyService
) {

    @GetMapping("/order")
    fun getTaskOrder(): ResponseEntity<List<String>?> {
        val result = modelService.loadTaskOrder()
        return ResponseEntity.ok(result)
    }

    @GetMapping("/sankey")
    fun getSankeyData(): ResponseEntity<SankeyData?> {
        val result = sankeyService.generateSankeyData()
        return ResponseEntity.ok(result)
    }

    @GetMapping("/materials")
    fun getMaterialRequirements(): ResponseEntity<List<TaskMaterialRequirements>> {
        val materials = materialService.extractMaterialRequirements()
        return ResponseEntity.ok(materials)
    }
}