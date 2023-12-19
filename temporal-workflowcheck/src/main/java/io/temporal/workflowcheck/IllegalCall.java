package io.temporal.workflowcheck;

public record IllegalCall(
        InvalidMethod method,
        int line) {
}
