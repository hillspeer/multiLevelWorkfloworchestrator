package com.tuc.workflow.orchestrator.builder;

import com.workflow.orchestrator.model.StepContext;
import com.workflow.orchestrator.model.StepResult;
import com.workflow.orchestrator.model.Workflow;
import com.workflow.orchestrator.registry.WorkflowRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class NestedWorkflowRouteBuilder extends RouteBuilder {
    
    @Autowired
    private WorkflowRegistry workflowRegistry;
    
    @Override
    public void configure() {

        from("grpc:0.0.0.0:9090/com.example.WorkflowService:Execute?synchronous=true")
            .routeId("workflow-grpc-entry")


            .setHeader("workflowId", simple("${body.workflowId}"))

            .toD("direct:execute-${header.workflowId}")
            
            .log("✅ Workflow Complete: ${header.workflowId}");

        workflowRegistry.getAllWorkflows().forEach(this::buildWorkflowRoute);
    }
    
    private void buildWorkflowRoute(Workflow workflow) {
        String workflowId = workflow.workflowId();
        
        from("direct:execute-" + workflowId)
            .routeId("workflow-" + workflowId)
            .log("🔄 Starting workflow: {} (parallel={})", workflowId, workflow.allowParallelExecution())
            

            .process(this::initStepContext)
            

            .process(this::executeWorkflowSteps)
            

            .process(this::buildGrpcResponse)
            
            .log("🏁 Workflow {} finished", workflowId);
    }
    

    private void initStepContext(Exchange exchange) {
        String workflowId = exchange.getIn().getHeader("workflowId", String.class);
        Workflow workflow = workflowRegistry.getWorkflow(workflowId);
        
        StepContext context = StepContext.builder()
            .workflowId(workflowId)
            .sharedContext(new HashMap<>())
            .parameters(Map.of("input", exchange.getIn().getHeader("inputData")))
            .build();
            
        exchange.setProperty("stepContext", context);
        log.debug("Initialized StepContext for {}", workflowId);
    }
    

    private void executeWorkflowSteps(Exchange exchange) {
        StepContext context = exchange.getProperty("stepContext", StepContext.class);
        Workflow workflow = workflowRegistry.getWorkflow(context.workflowId());
        
        log.info("Executing {} steps for workflow {}",
                workflow.getSteps().size(), context.workflowId());
        
        try {

            executeStepsSequentially(context, workflow.getSteps());
            

            if (workflow.allowParallelExecution()) {
                executeStepsParallel(context, workflow.getParallelSteps());
            }
            
            context.setStatus("COMPLETED");
            
        } catch (Exception e) {
            context.setStatus("FAILED");
            context.getSharedContext().put("error", e.getMessage());
            log.error("Workflow {} failed", context.workflowId(), e);
        }
        
        exchange.setProperty("stepContext", context);
    }
    

    private void executeStepsSequentially(StepContext context, List<Step> steps) {
        for (Step step : steps) {
            log.info("Sequential: {}", step.id());
            StepResult result = step.execute(context);
            
            if (!result.success()) {
                throw new RuntimeException("Step failed: " + result.errorMessage());
            }
        }
    }
    

    private void executeStepsParallel(StepContext context, List<Step> steps) {
        steps.parallelStream().forEach(step -> {
            log.info("Parallel: {}", step.id());
            StepResult result = step.execute(context);
            if (!result.success()) {
                log.warn("Parallel step failed (continuing): {}", result.errorMessage());
            }
        });
    }
    

    private void buildResponse(Exchange exchange) {
        StepContext context = exchange.getProperty("stepContext", StepContext.class);
        

        WorkflowResponse response = WorkflowResponse.newBuilder()
            .setWorkflowId(context.workflowId())
            .setStatus(context.getStatus())
            .setResults((Map<String, Object>) context.getSharedContext())
            .build();
            
        exchange.getIn().setBody(response);
    }
}
