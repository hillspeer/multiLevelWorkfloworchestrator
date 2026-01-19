package com.tuc.workflow.orchestrator.steps;

import lombok.Data;

import java.util.List;

@Data
public class StepDefinition {
    private String id;
    private String type;        // "local", "remote-http", "remote-grpc"
    private String processorClass;  // For local steps
    private String endpointUri;     // For remote steps
    private String threadPool;      // Thread pool name
    private List<StepDefinition> nestedSteps;
    private boolean parallel;       // Execute in parallel?
    private String outputHeader;    // Store result in this header
    private StepDefinition nestStep; // next step in flow
}
