package com.tuc.workflow.orchestrator.engine;

import com.tuc.workflow.orchestrator.steps.StepDefinition;

import java.util.List;

@Data
@Builder
public class WorkflowDefinition {
    private String workflowId;
    private String name;
    private List<StepDefinition> Steps;
}