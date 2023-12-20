package io.temporal.workflowcheck.testdata;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import io.temporal.workflowcheck.WorkflowCheck;

import java.util.Date;

@WorkflowInterface
public interface Suppression {
  @WorkflowMethod
  void suppression();

  class SuppressionImpl implements Suppression {
    @Override
    public void suppression() {
      // INVALID_CALL: Indirect invalid call
      //   * class: io/temporal/workflowcheck/testdata/Suppression$SuppressionImpl
      //   * method: suppression()V
      //   * callClass: io/temporal/workflowcheck/testdata/Suppression$SuppressionImpl
      //   * callMethod: badThing()V
      //   * callCauseClass: java/util/Date
      //   * callCauseMethod: <init>()V
      badThing();

      // Suppressed
      badThingSuppressed();

      // INVALID_CALL: Indirect invalid call after suppression
      //   * class: io/temporal/workflowcheck/testdata/Suppression$SuppressionImpl
      //   * method: suppression()V
      //   * callClass: io/temporal/workflowcheck/testdata/Suppression$SuppressionImpl
      //   * callMethod: badThing()V
      //   * callCauseClass: java/util/Date
      //   * callCauseMethod: <init>()V
      badThing();

      // INVALID_CALL: Partially suppressed
      //   * class: io/temporal/workflowcheck/testdata/Suppression$SuppressionImpl
      //   * method: suppression()V
      //   * callClass: io/temporal/workflowcheck/testdata/Suppression$SuppressionImpl
      //   * callMethod: badThingPartiallySuppressed()V
      //   * callCauseClass: io/temporal/workflowcheck/testdata/Suppression$SuppressionImpl
      //   * callCauseMethod: badThing()V
      badThingPartiallySuppressed();

      // Suppress all warnings
      WorkflowCheck.suppressWarnings();
      badThing();
      new Date();
      WorkflowCheck.restoreWarnings();

      // Suppress only warnings for badThing
      WorkflowCheck.suppressWarnings("badThing");
      badThing();
      // INVALID_CALL: Not suppressed
      //   * class: io/temporal/workflowcheck/testdata/Suppression$SuppressionImpl
      //   * method: suppression()V
      //   * callClass: java/util/Date
      //   * callMethod: <init>()V
      new Date();
      WorkflowCheck.restoreWarnings();

      // Suppress only warnings for date init
      WorkflowCheck.suppressWarnings("Date.<init>");
      // INVALID_CALL: Not suppressed
      //   * class: io/temporal/workflowcheck/testdata/Suppression$SuppressionImpl
      //   * method: suppression()V
      //   * callClass: io/temporal/workflowcheck/testdata/Suppression$SuppressionImpl
      //   * callMethod: badThing()V
      //   * callCauseClass: java/util/Date
      //   * callCauseMethod: <init>()V
      badThing();
      new Date();
      WorkflowCheck.restoreWarnings();

      // Suppress nested
      WorkflowCheck.suppressWarnings("Date.<init>");
      WorkflowCheck.suppressWarnings("badThing");
      badThing();
      new Date();
      WorkflowCheck.restoreWarnings();
      WorkflowCheck.restoreWarnings();

      // LOG: WARNING - 1 warning suppression(s) not restored in io/temporal/workflowcheck/testdata/Suppression$SuppressionImpl.suppression()V
      WorkflowCheck.suppressWarnings("never-restored");

      // LOG: WARNING - WorkflowCheck.suppressWarnings call not using string literal at io/temporal/workflowcheck/testdata/Suppression$SuppressionImpl.suppression()V (Suppression.java:90)
      var warningVar = "not-literal";
      WorkflowCheck.suppressWarnings(warningVar);
    }

    private static void badThing() {
      new Date();
    }

    @WorkflowCheck.SuppressWarnings
    private static void badThingSuppressed() {
      new Date();
    }

    @WorkflowCheck.SuppressWarnings(invalidCalls = "Date.<init>")
    private static void badThingPartiallySuppressed() {
      new Date();
      badThing();
    }
  }

  @WorkflowCheck.SuppressWarnings
  class SuppressionImpl2 implements Suppression {
    @Override
    public void suppression() {
      SuppressionImpl.badThing();
      new Date();
    }
  }

  // We just added another param here to confirm annotation array handling
  @WorkflowCheck.SuppressWarnings(invalidCalls = {"badThing", "some-other-param"})
  class SuppressionImpl3 implements Suppression {
    @Override
    public void suppression() {
      SuppressionImpl.badThing();
      // INVALID_CALL: Not suppressed
      //   * class: io/temporal/workflowcheck/testdata/Suppression$SuppressionImpl3
      //   * method: suppression()V
      //   * callClass: java/util/Date
      //   * callMethod: <init>()V
      new Date();
    }
  }

  @WorkflowCheck.SuppressWarnings(invalidCalls = "Date.<init>")
  class SuppressionImpl4 implements Suppression {
    @Override
    public void suppression() {
      // INVALID_CALL: Not suppressed
      //   * class: io/temporal/workflowcheck/testdata/Suppression$SuppressionImpl4
      //   * method: suppression()V
      //   * callClass: io/temporal/workflowcheck/testdata/Suppression$SuppressionImpl
      //   * callMethod: badThing()V
      //   * callCauseClass: java/util/Date
      //   * callCauseMethod: <init>()V
      SuppressionImpl.badThing();
      new Date();
    }
  }
}
