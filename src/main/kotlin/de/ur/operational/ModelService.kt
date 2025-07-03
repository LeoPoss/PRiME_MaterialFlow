package de.ur.operational

import org.springframework.stereotype.Service

@Service
class ModelService {

    fun loadTaskOrder(bpmnPath: String): List<String> {
//        val xmlFilePath = "src/main/resources/processes/MaterialFlow.bpmn"
        return BpmnProcessor(bpmnPath).loadTaskOrder()
    }
}