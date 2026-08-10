package com.example.app.service.tool;

/**
 * Marker interface for Spring beans that contain @Tool-annotated methods.
 *
 * Implementing this interface allows {@link ToolRegistry} to discover and
 * collect all tool beans via Spring injection (List&lt;ToolComponent&gt;).
 */
public interface ToolComponent {
}
