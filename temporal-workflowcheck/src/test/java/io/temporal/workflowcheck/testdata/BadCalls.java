package io.temporal.workflowcheck.testdata;

import io.temporal.workflow.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Random;

@WorkflowInterface
public interface BadCalls {
  @WorkflowMethod
  void doWorkflow();

  @SignalMethod
  void doSignal();

  @QueryMethod
  long doQuery();

  @UpdateMethod
  void doUpdate();

  @UpdateValidatorMethod(updateName = "doUpdate")
  void doUpdateValidate();

  class BadCallsImpl implements BadCalls {
    private static final String FIELD_FINAL = "foo";
    private static String FIELD_NON_FINAL = "bar";

    @Override
    public void doWorkflow() {
      // INVALID: Direct invalid call in workflow
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doWorkflow()V
      //   * accessedClass: java/time/Instant
      //   * accessedMember: now()Ljava/time/Instant;
      Instant.now();

      // INVALID: Indirect invalid call via local method
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doWorkflow()V
      //   * accessedClass: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * accessedMember: currentInstant()V
      //   * accessedCauseClass: java/util/Date
      //   * accessedCauseMethod: <init>()V
      currentInstant();

      // INVALID: Indirect invalid call via stdlib method
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doWorkflow()V
      //   * accessedClass: java/util/Collections
      //   * accessedMember: shuffle(Ljava/util/List;)V
      //   * accessedCauseClass: java/util/Random
      //   * accessedCauseMethod: <init>()V
      Collections.shuffle(new ArrayList<>());

      // But this is an acceptable call because we are passing in a seeded random
      Collections.shuffle(new ArrayList<>(), new Random(123));

      // INVALID: Configured invalid field
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doWorkflow()V
      //   * accessedClass: java/lang/System
      //   * accessedMember: out
      System.out.println("foo");

      // INVALID: Setting static non-final field
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doWorkflow()V
      //   * accessedClass: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * accessedMember: FIELD_NON_FINAL
      FIELD_NON_FINAL = "blah";

      // INVALID: Getting static non-final field
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doWorkflow()V
      //   * accessedClass: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * accessedMember: FIELD_NON_FINAL
      new StringBuilder(FIELD_NON_FINAL);

      // It's ok to access a final static field though
      new StringBuilder(FIELD_FINAL);
    }

    @Override
    public void doSignal() {
      // INVALID: Direct invalid call in signal
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doSignal()V
      //   * accessedClass: java/lang/System
      //   * accessedMember: nanoTime()J
      System.nanoTime();
    }

    @Override
    public long doQuery() {
      // INVALID: Direct invalid call in query
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doQuery()J
      //   * accessedClass: java/lang/System
      //   * accessedMember: currentTimeMillis()J
      return System.currentTimeMillis();
    }

    @Override
    public void doUpdate() {
      // INVALID: Direct invalid call in update
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doUpdate()V
      //   * accessedClass: java/time/LocalDate
      //   * accessedMember: now()Ljava/time/LocalDate;
      LocalDate.now();
    }

    @Override
    public void doUpdateValidate() {
      // INVALID: Direct invalid call in update validator
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doUpdateValidate()V
      //   * accessedClass: java/time/LocalDateTime
      //   * accessedMember: now()Ljava/time/LocalDateTime;
      LocalDateTime.now();
    }

    private void currentInstant() {
      new Date();
    }
  }
}