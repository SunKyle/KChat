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

    // ── Phase enum (replaces magic-number order boundaries) ──────────

    enum Phase {
        /** Input sanitization, language detection, web search, memory recall */
        PREPROCESS,
        /** Prompt template rendering, memory formatting, message assembly, token management */
        ASSEMBLY,
        /** Model routing and LLM invocation */
        EXECUTION,
        /** Tool call parsing, tool invocation, agent goal evaluation (Phase 3) */
        AGENT,
        /** Memory update, message persistence, memory extraction, title generation */
        POSTPROCESS,
        /** Metrics recording, pipeline auditing */
        OBSERVABILITY
    }

    // ── Interface methods ────────────────────────────────────────────

    /** Unique stage name for logging, metrics, and composition references. */
    String getName();

    /** Execute this stage's logic against the context. Mutate context in-place. */
    void execute(ConversationContext ctx);

    /**
     * Which phase this stage belongs to. Used by the executor for phase-based
     * filtering (e.g., executeStreaming runs PREPROCESS through EXECUTION,
     * executePostProcessing runs POSTPROCESS through OBSERVABILITY).
     */
    Phase getPhase();

    /**
     * Whether this stage should run for the given context.
     * Override to skip stages based on pipeline type, context state, or configuration.
     */
    default boolean isApplicable(ConversationContext ctx) {
        return true;
    }

    /**
     * Relative ordering within a phase. Lower values execute first.
     * Convention: use multiples of 100 to leave room for insertion.
     *
     * <pre>
     * PREPROCESS:     100-399 (sanitization, search, memory recall)
     * ASSEMBLY:       400-499 (prompt building, token management)
     * EXECUTION:      500-599 (model routing, LLM invocation)
     * AGENT:          600-699 (tool calls, agent loop — Phase 3)
     * POSTPROCESS:    700-899 (persistence, memory extraction)
     * OBSERVABILITY:  900+    (metrics, auditing)
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
