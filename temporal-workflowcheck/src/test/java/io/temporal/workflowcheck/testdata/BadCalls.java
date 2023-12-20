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
    @Override
    public void doWorkflow() {
      // INVALID_CALL: Direct invalid call in workflow
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doWorkflow()V
      //   * callClass: java/time/Instant
      //   * callMethod: now()Ljava/time/Instant;
      Instant.now();

      // INVALID_CALL: Indirect invalid call via local method
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doWorkflow()V
      //   * callClass: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * callMethod: currentInstant()V
      //   * callCauseClass: java/util/Date
      //   * callCauseMethod: <init>()V
      currentInstant();

      // INVALID_CALL: Indirect invalid call via stdlib method
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doWorkflow()V
      //   * callClass: java/util/Collections
      //   * callMethod: shuffle(Ljava/util/List;)V
      //   * callCauseClass: java/util/Random
      //   * callCauseMethod: <init>()V
      Collections.shuffle(new ArrayList<>());

      // But this is an acceptable call because we are passing in a seeded random
      Collections.shuffle(new ArrayList<>(), new Random(123));
    }

    @Override
    public void doSignal() {
      // INVALID_CALL: Direct invalid call in signal
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doSignal()V
      //   * callClass: java/lang/System
      //   * callMethod: nanoTime()J
      System.nanoTime();
    }

    @Override
    public long doQuery() {
      // INVALID_CALL: Direct invalid call in query
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doQuery()J
      //   * callClass: java/lang/System
      //   * callMethod: currentTimeMillis()J
      return System.currentTimeMillis();
    }

    @Override
    public void doUpdate() {
      // INVALID_CALL: Direct invalid call in update
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doUpdate()V
      //   * callClass: java/time/LocalDate
      //   * callMethod: now()Ljava/time/LocalDate;
      LocalDate.now();
    }

    @Override
    public void doUpdateValidate() {
      // INVALID_CALL: Direct invalid call in update validator
      //   * class: io/temporal/workflowcheck/testdata/BadCalls$BadCallsImpl
      //   * method: doUpdateValidate()V
      //   * callClass: java/time/LocalDateTime
      //   * callMethod: now()Ljava/time/LocalDateTime;
      LocalDateTime.now();
    }

    private void currentInstant() {
      new Date();
    }
  }
}