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
      // INVALID: Set iteration
      //   * class: io/temporal/workflowcheck/testdata/UnsafeIteration$UnsafeIterationImpl
      //   * method: unsafeIteration()V
      //   * accessedClass: java/util/Set
      //   * accessedMember: iterator()Ljava/util/Iterator;
      for (var kv : Map.of("a", "b").entrySet()) {
        kv.getKey();
      }

      var sortedMapEntries = new TreeMap<>(Map.of("a", "b")).entrySet();
      // INVALID: Set iteration, sadly even if the map is deterministic
      //   * class: io/temporal/workflowcheck/testdata/UnsafeIteration$UnsafeIterationImpl
      //   * method: unsafeIteration()V
      //   * accessedClass: java/util/Set
      //   * accessedMember: iterator()Ljava/util/Iterator;
      for (var kv : sortedMapEntries) {
        kv.getKey();
      }

      // SortedSet iteration is safe
      for (var v : new TreeSet<>(Set.of("a", "b"))) {
        v.length();
      }

      // So is LinkedHashSet
      for (var v : new LinkedHashSet<>(Set.of("a", "b"))) {
        v.length();
      }

      // ArrayDeque is safe
      for (var v : new ArrayDeque<>(Set.of("a", "b"))) {
        v.length();
      }

      // Most streams are safe, except for sets
      Stream.of("a", "b");
      List.of("a", "b").stream();
      // INVALID: Set streams
      //   * class: io/temporal/workflowcheck/testdata/UnsafeIteration$UnsafeIterationImpl
      //   * method: unsafeIteration()V
      //   * accessedClass: java/util/Set
      //   * accessedMember: stream()Ljava/util/stream/Stream;
      Set.of("a", "b").stream().forEach(a -> { });
    }
  }

}
