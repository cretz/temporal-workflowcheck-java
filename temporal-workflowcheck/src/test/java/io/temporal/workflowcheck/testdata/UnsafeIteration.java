package io.temporal.workflowcheck.testdata;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.*;
import java.util.stream.Stream;

@WorkflowInterface
public interface UnsafeIteration {
  @WorkflowMethod
  void unsafeIteration();

  class UnsafeIterationImpl implements UnsafeIteration {
    @Override
    public void unsafeIteration() {
      // INVALID_CALL: Set iteration
      //   * class: io/temporal/workflowcheck/testdata/UnsafeIteration$UnsafeIterationImpl
      //   * method: unsafeIteration()V
      //   * callClass: java/util/Set
      //   * callMethod: iterator()Ljava/util/Iterator;
      for (var kv : Map.of("a", "b").entrySet()) {
        kv.notify();
      }

      var sortedMapEntries = new TreeMap<>(Map.of("a", "b")).entrySet();
      // INVALID_CALL: Set iteration, sadly even if the map is deterministic
      //   * class: io/temporal/workflowcheck/testdata/UnsafeIteration$UnsafeIterationImpl
      //   * method: unsafeIteration()V
      //   * callClass: java/util/Set
      //   * callMethod: iterator()Ljava/util/Iterator;
      for (var kv : sortedMapEntries) {
        kv.notify();
      }

      // SortedSet iteration is safe
      for (var kv : new TreeSet<>(Set.of("a", "b"))) {
        kv.notify();
      }

      // Most streams are safe, except for sets
      Stream.of("a", "b");
      List.of("a", "b").stream();
      // INVALID_CALL: Set streams
      //   * class: io/temporal/workflowcheck/testdata/UnsafeIteration$UnsafeIterationImpl
      //   * method: unsafeIteration()V
      //   * callClass: java/util/Set
      //   * callMethod: stream()Ljava/util/stream/Stream;
      Set.of("a", "b").stream().forEach(a -> { });

      // TODO(cretz): Parallel stream on otherwise-safe collections?
    }
  }

}
