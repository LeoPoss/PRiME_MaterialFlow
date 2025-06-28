package de.ur.operational

import org.springframework.stereotype.Service

@Service
class ModelService {

    fun loadTaskOrder(): List<String> {
        val xmlFilePath = "src/main/resources/processes/testflow.bpmn"
        return BpmnProcessor(xmlFilePath).loadTaskOrder()
    }
}