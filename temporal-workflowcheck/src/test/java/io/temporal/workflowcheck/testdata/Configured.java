package io.temporal.workflowcheck.testdata;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface Configured {
  @WorkflowMethod
  void configured();

  class ConfiguredImpl implements Configured {
    @Override
    public void configured() {
      // INVALID_CALL: Configured invalid
      //   * class: io/temporal/workflowcheck/testdata/Configured$ConfiguredImpl
      //   * method: configured()V
      //   * callClass: io/temporal/workflowcheck/testdata/Configured$SomeCalls
      //   * callMethod: configuredInvalidFull()V
      new SomeCalls().configuredInvalidFull();

      // INVALID_CALL: Configured invalid
      //   * class: io/temporal/workflowcheck/testdata/Configured$ConfiguredImpl
      //   * method: configured()V
      //   * callClass: io/temporal/workflowcheck/testdata/Configured$SomeCalls
      //   * callMethod: configuredInvalidALlButDescriptor()V
      new SomeCalls().configuredInvalidALlButDescriptor();

      // INVALID_CALL: Configured invalid
      //   * class: io/temporal/workflowcheck/testdata/Configured$ConfiguredImpl
      //   * method: configured()V
      //   * callClass: io/temporal/workflowcheck/testdata/Configured$SomeCalls
      //   * callMethod: configuredInvalidClassAndMethod()V
      new SomeCalls().configuredInvalidClassAndMethod();

      // INVALID_CALL: Configured invalid
      //   * class: io/temporal/workflowcheck/testdata/Configured$ConfiguredImpl
      //   * method: configured()V
      //   * callClass: io/temporal/workflowcheck/testdata/Configured$SomeCalls
      //   * callMethod: configuredInvalidJustName()V
      new SomeCalls().configuredInvalidJustName();

      // INVALID_CALL: Calls configured invalid
      //   * class: io/temporal/workflowcheck/testdata/Configured$ConfiguredImpl
      //   * method: configured()V
      //   * callClass: io/temporal/workflowcheck/testdata/Configured$SomeCalls
      //   * callMethod: callsConfiguredInvalid()V
      //   * callCauseClass: io/temporal/workflowcheck/testdata/Configured$SomeCalls
      //   * callCauseMethod: configuredInvalidJustName()V
      new SomeCalls().callsConfiguredInvalid();

      // This overload is ok
      new SomeCalls().configuredInvalidOverload("");

      // INVALID_CALL: Configured invalid
      //   * class: io/temporal/workflowcheck/testdata/Configured$ConfiguredImpl
      //   * method: configured()V
      //   * callClass: io/temporal/workflowcheck/testdata/Configured$SomeCalls
      //   * callMethod: configuredInvalidOverload(I)V
      new SomeCalls().configuredInvalidOverload(0);

      // INVALID_CALL: Configured invalid
      //   * class: io/temporal/workflowcheck/testdata/Configured$ConfiguredImpl
      //   * method: configured()V
      //   * callClass: io/temporal/workflowcheck/testdata/Configured$SomeInterface$SomeInterfaceImpl
      //   * callMethod: configuredInvalidIface()V
      new SomeInterface.SomeInterfaceImpl().configuredInvalidIface();

      // INVALID_CALL: Configured invalid
      //   * class: io/temporal/workflowcheck/testdata/Configured$ConfiguredImpl
      //   * method: configured()V
      //   * callClass: io/temporal/workflowcheck/testdata/Configured$ConfiguredInvalidClass
      //   * callMethod: someMethod()V
      ConfiguredInvalidClass.someMethod();
    }
  }

  class SomeCalls {
    void configuredInvalidFull() {
    }

    void configuredInvalidALlButDescriptor() {
    }

    void configuredInvalidClassAndMethod() {
    }

    void configuredInvalidJustName() {
    }

    void callsConfiguredInvalid() {
      configuredInvalidJustName();
    }

    void configuredInvalidOverload(String param) {
    }

    void configuredInvalidOverload(int param) {
    }
  }

  interface SomeInterface {
    void configuredInvalidIface();

    class SomeInterfaceImpl implements SomeInterface {
      @Override
      public void configuredInvalidIface() {
      }
    }
  }

  class ConfiguredInvalidClass {
    static void someMethod() {
    }
  }
}
