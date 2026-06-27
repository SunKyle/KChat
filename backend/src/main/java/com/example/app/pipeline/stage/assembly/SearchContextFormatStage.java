package com.example.app.pipeline.stage.assembly;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class SearchContextFormatStage implements ContextPipelineStage {

    @Override
    public String getName() {
        return "searchContextFormatStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        String searchContext = ctx.getSearchContext();
        String formatted = formatSearchContext(searchContext);
        ctx.getAgentState().put("formattedSearchContext", formatted);
    }

    private String formatSearchContext(String searchContext) {
        if (searchContext == null || searchContext.isBlank()) {
            return "";
        }
        String now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss EEEE"));
        return "当前时间：" + now
                + "\n\n网络搜索结果：\n" + searchContext
                + "\n\n请基于以上网络搜索结果回答用户问题。如果搜索结果不足以回答问题，请结合你的知识进行补充。";
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.getSearchContext() != null && !ctx.getSearchContext().isBlank();
    }

    @Override
    public int getOrder() {
        return 420;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
