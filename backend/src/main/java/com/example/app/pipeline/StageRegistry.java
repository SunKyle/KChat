package com.example.app.pipeline;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Auto-discovers all {@link ContextPipelineStage} beans via Spring injection
 * and provides lookup by name, type, and ordered iteration.
 *
 * Stages are injected as a List<ContextPipelineStage> — Spring automatically
 * collects all beans implementing the interface.
 */
@Component
@Slf4j
public class StageRegistry {

    private final List<ContextPipelineStage> stages;
    private final Map<String, ContextPipelineStage> stageByName;

    public StageRegistry(List<ContextPipelineStage> stages) {
        this.stages = new ArrayList<>(stages);
        this.stages.sort(Comparator.comparingInt(ContextPipelineStage::getOrder));
        this.stageByName = new LinkedHashMap<>();
        for (ContextPipelineStage stage : this.stages) {
            stageByName.put(stage.getName(), stage);
        }
    }

    /** All registered stages, sorted by {@link ContextPipelineStage#getOrder()}. */
    public List<ContextPipelineStage> getAllStages() {
        return Collections.unmodifiableList(stages);
    }

    /** Lookup a stage by its unique name. */
    public Optional<ContextPipelineStage> getStage(String name) {
        return Optional.ofNullable(stageByName.get(name));
    }

    /** Lookup a stage by its concrete type. */
    @SuppressWarnings("unchecked")
    public <T extends ContextPipelineStage> Optional<T> getStage(Class<T> type) {
        return stages.stream()
                .filter(type::isInstance)
                .map(s -> (T) s)
                .findFirst();
    }

    @PostConstruct
    public void logRegisteredStages() {
        log.info("Pipeline Stage Registry initialized with {} stages:", stages.size());
        for (ContextPipelineStage stage : stages) {
            log.info("  [{:03d}] {} (critical={})", stage.getOrder(), stage.getName(), stage.isCritical());
        }
    }
}
