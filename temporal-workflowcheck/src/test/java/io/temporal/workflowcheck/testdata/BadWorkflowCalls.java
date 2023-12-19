package io.temporal.workflowcheck.testdata;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.time.Instant;

public class BadWorkflowCalls {
  @WorkflowInterface
  public interface BadWorkflow {
    @WorkflowMethod
    void doWorkflow();
  }

  public static class BadWorkflowImpl implements BadWorkflow {
    @Override
    public void doWorkflow() {
      Instant.now();
    }
  }
}