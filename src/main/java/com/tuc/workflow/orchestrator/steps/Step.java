package com.tuc.workflow.orchestrator.steps;

public interface Step<I,O> {

    O execute(I input);
}
