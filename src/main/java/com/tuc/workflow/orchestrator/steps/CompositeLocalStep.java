package com.tuc.workflow.orchestrator.steps;

public class CompositeLocalStep extends LocalStep implements Step<StepContext, StepResult> {
    
    private final List<Step> nestedSteps;
    
    public CompositeLocalStep(String id, StepExecutor outerExecutor, 
                            boolean parallel, List<Step> nestedSteps) {
        super(id, outerExecutor, parallel);
        this.nestedSteps = nestedSteps;
    }
    
    @Override
    public StepResult execute(StepContext context) {
        log.info("📂 Composite {}: {} nested steps", id(), nestedSteps.size());
        
        // Execute outer logic first
        StepResult outerResult = super.execute(context);
        if (!outerResult.success()) {
            return outerResult;
        }
        
        // ⭐ EXECUTE NESTED STEPS SEQUENTIALLY
        for (Step nested : nestedSteps) {
            StepResult nestedResult = nested.execute(context);
            if (!nestedResult.success()) {
                return StepResult.failure(id(), 
                    "Nested step '" + nested.id() + "' failed: " + nestedResult.errorMessage(), 
                    null);
            }
        }
        
        return StepResult.success(id(), context.getSharedContext());
    }
}
