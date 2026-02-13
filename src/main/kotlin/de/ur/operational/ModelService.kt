package de.ur.operational

import org.springframework.stereotype.Service

@Service
class ModelService(private val bpmnProcessor: BpmnProcessor) {

    fun loadTaskOrder(bpmnPath: String): List<String> {
        return bpmnProcessor.loadTaskOrder(bpmnPath)
    }
}