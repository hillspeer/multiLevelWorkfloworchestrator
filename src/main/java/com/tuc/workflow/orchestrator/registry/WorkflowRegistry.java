package com.tuc.workflow.orchestrator.registry;

import com.workflow.orchestrator.model.Workflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry to manage and retrieve workflow definitions.
 */
@Slf4j
@Component
public class WorkflowRegistry {
    
    private final Map<String, Workflow> workflows = new ConcurrentHashMap<>();
    
    /**
     * Register a workflow definition.
     */
    public void registerWorkflow(Workflow workflow) {
        workflows.put(workflow.getWorkflowId(), workflow);
        log.info("Registered workflow: {} - {}", workflow.getWorkflowId(), workflow.getName());
    }
    
    /**
     * Get a workflow by ID.
     */
    public Workflow getWorkflow(String workflowId) {
        return workflows.get(workflowId);
    }
    
    /**
     * Check if a workflow exists.
     */
    public boolean hasWorkflow(String workflowId) {
        return workflows.containsKey(workflowId);
    }
    
    /**
     * Get all registered workflow IDs.
     */
    public java.util.Set<String> getAllWorkflowIds() {
        return workflows.keySet();
    }
}
