package com.example.app.pipeline;

import com.example.app.pipeline.context.ConversationContext;

/**
 * A single composable stage in the context pipeline.
 *
 * Each stage has a single responsibility and operates on the shared
 * {@link ConversationContext}. Stages are discovered by the {@link StageRegistry}
 * via Spring dependency injection and executed by the {@link ContextPipelineExecutor}.
 *
 * Design rules:
 * - Stages MUST NOT call other stages — all communication via ConversationContext
 * - Stages MUST be stateless singletons — all state in ConversationContext
 * - Stages MUST be idempotent with respect to the context fields they write
 */
public interface ContextPipelineStage {

    /** Unique stage name for logging, metrics, and composition references. */
    String getName();

    /** Execute this stage's logic against the context. Mutate context in-place. */
    void execute(ConversationContext ctx);

    /**
     * Whether this stage should run for the given context.
     * Override to skip stages based on pipeline type, context state, or configuration.
     */
    default boolean isApplicable(ConversationContext ctx) {
        return true;
    }

    /**
     * Relative ordering within the pipeline. Lower values execute first.
     * Convention: use multiples of 100 to leave room for insertion.
     *
     * <pre>
     * 100-399: Pre-processing   (sanitization, search, memory recall)
     * 400-499: Assembly         (prompt building, token management)
     * 500-599: Execution        (model routing, LLM invocation)
     * 600-699: Agent/Tool       (tool calls, agent loop)
     * 700-899: Post-processing  (persistence, memory extraction)
     * 900+:    Observability    (metrics, auditing)
     * </pre>
     */
    default int getOrder() {
        return 500;
    }

    /**
     * Whether a failure in this stage should halt the pipeline.
     * Non-critical stages (metrics, title generation) should return false
     * so they don't break the core chat response.
     */
    default boolean isCritical() {
        return true;
    }
}
