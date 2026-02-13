package de.ur.operational.process.listener

import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.ExecutionListener
import org.camunda.bpm.model.bpmn.instance.FlowNode
import org.springframework.stereotype.Component

@Component
class NextTaskListener : ExecutionListener {
    
    override fun notify(execution: DelegateExecution) {
        val currentNode = execution.bpmnModelElementInstance as? FlowNode
        
        if (currentNode != null) {
            val outgoingFlows = currentNode.outgoing
            if (outgoingFlows.isNotEmpty()) {
                val firstFlow = outgoingFlows.first()
                val targetNode = firstFlow.target
                execution.setVariable("nextTask", targetNode.id)
            }
        }
    }
}
