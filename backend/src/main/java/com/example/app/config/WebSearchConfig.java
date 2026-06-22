package com.example.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "websearch")
public class WebSearchConfig {

    private boolean enabled = true;
    private int timeoutSeconds = 8;
    private int maxResults = 5;
    private String engine = "bing";
    private String bingApiKey = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }

    public String getBingApiKey() { return bingApiKey; }
    public void setBingApiKey(String bingApiKey) { this.bingApiKey = bingApiKey; }
}
