package io.temporal.workflowcheck;

import java.util.List;

public record InvalidMethod(
        String methodDescriptor,
        String fileName,
        List<IllegalCall> illegalCalls,
        boolean inherentlyIllegal) {
}
