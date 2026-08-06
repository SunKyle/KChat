package com.example.app.config;

import com.example.app.entity.PromptTemplate;
import com.example.app.repository.PromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PromptTemplateMigrationRunner implements ApplicationRunner {

    private final PromptTemplateRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Optional<PromptTemplate> existing =
                    repository.findActiveLatestVersionByName(DefaultSystemPrompt.NAME);
            if (existing.isPresent()) {
                PromptTemplate template = existing.get();
                if (template.getVersion() >= DefaultSystemPrompt.VERSION) {
                    return;
                }
                template.setContent(DefaultSystemPrompt.CONTENT);
                template.setDefaults(DefaultSystemPrompt.DEFAULTS);
                template.setVersion(DefaultSystemPrompt.VERSION);
                template.setActive(true);
                template.setUpdatedAt(LocalDateTime.now());
                repository.save(template);
                log.info("Upgraded default system prompt template to v{}", DefaultSystemPrompt.VERSION);
            } else {
                PromptTemplate template = PromptTemplate.builder()
                        .id(UUID.randomUUID().toString())
                        .name(DefaultSystemPrompt.NAME)
                        .content(DefaultSystemPrompt.CONTENT)
                        .description(DefaultSystemPrompt.DESCRIPTION)
                        .category(DefaultSystemPrompt.CATEGORY)
                        .defaults(DefaultSystemPrompt.DEFAULTS)
                        .version(DefaultSystemPrompt.VERSION)
                        .active(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                repository.save(template);
                log.info("Created default system prompt template v{}", DefaultSystemPrompt.VERSION);
            }
        } catch (Exception e) {
            log.warn("Failed to migrate default system prompt template: {}", e.getMessage());
        }
    }
}
