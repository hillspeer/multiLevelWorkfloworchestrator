package com.tuc.workflow.orchestrator.steps;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class StepRegistryService {
    
    @Autowired
    private CamelContext camelContext;
    
    @Autowired
    private ObjectMapper objectMapper;
    

    private final Map<String, StepDefinition> stepRegistry = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void loadStepRegistry(String workflow) {
        try {

            List<StepDefinition> steps = objectMapper.readValue(
                new ClassPathResource(workflow).getInputStream(),
                new TypeReference<List<StepDefinition>>() {}
            );

            steps.forEach(step -> {
                stepRegistry.put(step.getId(), step);

            });
            
            log.info("🎉 Step registry loaded: {} steps available", stepRegistry.size());

            buildDynamicWorkflowRoute();
            
        } catch (Exception e) {
            log.error(" Failed to load step registry", e);
            throw new RuntimeException(e);
        }
    }
    
    public StepDefinition getStep(String stepId) {
        return stepRegistry.get(stepId);
    }
    
    private void buildDynamicWorkflowRoute() {
        try {

            camelContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:subjectmatch")
                        .routeId("subjectmatch-workflow")

                        

                        .to("direct:sequential-steps")
                        

                        .multicast()
                            .executorServiceRef("parallel")
                            .parallelProcessing()
                                .to("direct:parallel-steps")
                        .end()
                        
                        .process(exchange -> {

                            log.info(" All registry steps complete");
                        });
                }
            });
            

            camelContext.addRoutes(new RouteBuilder() {
                @Override 
                public void configure() {
                    from("direct:sequential-steps")
                        .routeId("sequential-registry-steps")
                        .threads("local-fast")
                        .process(StepRegistryService.this::executeRegistryStep("validate-customer"));
                }
            });
            

            camelContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:parallel-steps")
                        .routeId("parallel-registry-steps")
                        .routeTemplate("registryStepTemplate")
                            .templateParameter("stepId")
                            .multicast()
                                .parallelProcessing()
                                .process(exchange -> 
                                    executeRegistryStep(exchange, "{{stepId}}"))
                            .end();
                }
            });
            
            log.info("Dynamic workflow routes built from registry");
            
        } catch (Exception e) {
            log.error("Failed to build dynamic routes", e);
        }
    }
    
    public void executeRegistryStep(String stepId) {

    }
    
    public void executeRegistryStep(Exchange exchange, String stepId) {
        StepDefinition step = stepRegistry.get(stepId);
        if (step == null) {
            throw new IllegalArgumentException("Step not found: " + stepId);
        }
        
        log.info("Executing step: {} ({})", stepId, step.getType());
        
        switch (step.getType()) {
            case "local":

                exchange.getContext().getRegistry().lookup(step.getProcessorClass())
                    .getClass().getMethod("process", Exchange.class)
                    .invoke(null, exchange);
                break;
            case "remote-http":
                exchange.getIn().setHeader(Exchange.HTTP_URI, step.getEndpointUri());
                exchange.getContext().createProducerTemplate()
                    .send(step.getEndpointUri(), exchange);
                break;
        }

        if (step.getOutputHeader() != null) {
            log.info("Step {} result → header: {}", stepId, step.getOutputHeader());
        }
    }
}
