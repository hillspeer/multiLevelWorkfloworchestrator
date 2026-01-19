package com.tuc.workflow.orchestrator.registry;

import com.tuc.workflow.orchestrator.engine.WorkflowDefinition;

import java.util.Optional;

@Service
@Slf4j
public class DynamicWorkflowRegistry {
    
    private final Map<String, WorkflowDefinition> registry = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void loadDynamicWorkflows() {

        WorkflowDefinition creditWorkflow = loadFromJson("matching-workflow");
        registry.put(creditWorkflow.getWorkflowId(), creditWorkflow);
        
        log.info("✅ Loaded {} dynamic workflows", registry.size());
    }

    private Optional<WorkflowDefinition> loadFromJson(String s) {
        WorkflowDefinition wd = new WorkflowDefinition();
        return new Optional<WorkflowDefinition>.of(wd);
    }

}