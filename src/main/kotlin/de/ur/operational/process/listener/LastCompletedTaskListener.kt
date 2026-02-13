package de.ur.operational.process.listener

import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.ExecutionListener
import org.springframework.stereotype.Component

@Component
class LastCompletedTaskListener : ExecutionListener {
    
    override fun notify(execution: DelegateExecution) {
        val currentActivityId = execution.currentActivityId
        execution.setVariable("lastCompletedTask", currentActivityId)
    }
}
