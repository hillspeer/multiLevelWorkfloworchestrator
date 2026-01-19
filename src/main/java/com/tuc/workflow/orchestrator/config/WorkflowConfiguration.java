package com.tuc.workflow.orchestrator.config;

import com.tuc.workflow.orchestrator.steps.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Configuration
public class WorkflowConfiguration {
    
    @Autowired
    private StepRegistryService registry;

    @Autowired
    private WorkflowRegistry workflowRegistry;

    private final Map<String, StepDefinition> stepRegistry = new ConcurrentHashMap<>();

    @PostConstruct
    public void registerDynamicWorkflows() {

        WorkflowDefinition dynamicWorkflow = registry.loadStepRegistry("subject-matching-workflow.json");
        
        Workflow workflow = Workflow.builder()
            .workflowId(dynamicWorkflow.getWorkflowId())
            .name(dynamicWorkflow.getName())
            .allowParallelExecution(true)
            .timeoutSeconds(60)
            .build();
            

        dynamicWorkflow.getSequentialSteps().forEach(this::stepToLocalStep);
        dynamicWorkflow.getParallelSteps().forEach(this::stepToLocalStep);
        
        workflowRegistry.registerWorkflow(workflow);

    }
    
    private void stepToLocalStep(StepDefinition stepDef, Workflow workflow) {
        LocalStep localStep = new LocalStep(stepDef.getId(), context -> {

            

            StepResult result = executeDynamicStep(context, stepDef);
            

            if ("composite".equals(stepDef.getType()) && result.isSuccess()) {
                result = executeNestedSteps(context, stepDef.getNestedSteps());
            }
            
            return result;
        }, stepDef.isParallel());
        
        camelWorkflow.addStep(localStep);
    }
    
    private StepResult executeDynamicStep(StepContext context, StepDefinition step) {
        switch (step.getType()) {
            case "local":
                return invokeProcessor(context, step.getProcessorClass());
            case "remote-http":
                return callRemoteApi(context, step.getEndpointUri());
            default:
                return StepResult.failure(step.getId(), "Unknown step type", null);
        }
    }
    
    private StepResult executeNestedSteps(StepContext context, List<StepDefinition> nestedSteps) {

        
        for (StepDefinition nested : nestedSteps) {
            StepResult result = executeDynamicStep(context, nested);
            if (!result.isSuccess()) {
                return result;
            }
        }
        return StepResult.success("nested-composite", context.getSharedContext());
    }

    StepResult invokeProcessor(StepContext context, String processor, StepDefinition stepDef){
        StepDefinition step = stepRegistry.get(stepId);

        if (step == null) {
            throw new IllegalArgumentException("Step not found: " + stepId);
        }

        from("direct:"+stepDef.getId()).process().to("");

        exchange.getContext().getRegistry().lookup(step.getProcessorClass())
                .getClass().getMethod("process", Exchange.class)
                .invoke(null, exchange);

        return new StepResult();
    }


}
