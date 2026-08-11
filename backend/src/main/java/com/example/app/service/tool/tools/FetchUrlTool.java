package com.example.app.service.tool.tools;

import com.example.app.service.tool.ToolComponent;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 网页抓取工具
 *
 * 根据 URL 获取网页内容，使用 Apache Tika 将 HTML 转换为纯文本，
 * 供 LLM 在 Agent 模式下阅读网页详细内容。
 *
 * <p>适用场景：LLM 通过 webSearch 获得搜索结果后，
 * 需要进一步访问某个结果页面获取详细信息时使用。
 */
@Slf4j
@Component
public class FetchUrlTool implements ToolComponent {

    private static final int MAX_CONTENT_LENGTH = 20000;
    private static final int FETCH_TIMEOUT_SECONDS = 15;
    private static final int TIKA_MAX_CHARS = 50000;

    private final HttpClient httpClient;
    private final AutoDetectParser tikaParser;

    public FetchUrlTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(FETCH_TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.tikaParser = new AutoDetectParser();
    }

    @Tool("""
            抓取指定 URL 的网页内容并返回纯文本。
            当需要查看某个网页的详细内容、阅读文章、获取信息时使用此工具。
            仅支持公开可访问的 URL（http:// 或 https:// 开头），不支持需要登录或验证码的页面。
            """)
    public String fetchUrl(String url) {

        if (url == null || url.isBlank()) {
            return "错误：URL 不能为空";
        }

        url = url.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "错误：仅支持 http:// 或 https:// 开头的 URL";
        }

        if (isBlockedHost(url)) {
            return "错误：不允许访问该地址";
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(FETCH_TIMEOUT_SECONDS))
                    .header("User-Agent", "Mozilla/5.0 (compatible; KChatBot/1.0)")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .GET()
                    .build();

            log.info("[FetchUrl] Fetching: {}", url);
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            int status = response.statusCode();
            if (status >= 400) {
                return "错误：HTTP " + status + "，无法访问该页面";
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("");
            String body = response.body();

            if (contentType.contains("html") || body.contains("<html") || body.contains("<HTML")) {
                body = htmlToText(body);
            }

            if (body == null || body.isBlank()) {
                return "错误：页面内容为空";
            }

            if (body.length() > MAX_CONTENT_LENGTH) {
                body = body.substring(0, MAX_CONTENT_LENGTH)
                        + "\n\n...（内容已截断，仅显示前 " + MAX_CONTENT_LENGTH + " 字符）";
            }

            String result = "【URL】" + url + "\n【内容】\n" + body;
            log.info("[FetchUrl] Fetched {} chars from {}", body.length(), url);
            return result;

        } catch (Exception e) {
            log.warn("[FetchUrl] Failed to fetch {}: {}", url, e.getMessage());
            return "错误：抓取失败 - " + e.getMessage();
        }
    }

    private String htmlToText(String html) {
        try {
            BodyContentHandler handler = new BodyContentHandler(TIKA_MAX_CHARS);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            tikaParser.parse(
                    new java.io.InputStream() {
                        private int pos = 0;
                        private final byte[] data = html.getBytes(StandardCharsets.UTF_8);

                        @Override
                        public int read() {
                            return pos < data.length ? (data[pos++] & 0xFF) : -1;
                        }
                    },
                    handler, metadata, context);
            return handler.toString().trim();
        } catch (Exception e) {
            log.warn("[FetchUrl] Tika HTML parse failed, using fallback: {}", e.getMessage());
            return stripHtmlTags(html);
        }
    }

    private String stripHtmlTags(String html) {
        return html
                .replaceAll("(?is)<script.*?</script>", "")
                .replaceAll("(?is)<style.*?</style>", "")
                .replaceAll("(?is)<head.*?</head>", "")
                .replaceAll("(?is)<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&[a-zA-Z]+;", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isBlockedHost(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return true;
            return host.equals("localhost")
                    || host.equals("127.0.0.1")
                    || host.equals("0.0.0.0")
                    || host.endsWith(".local")
                    || host.startsWith("10.")
                    || host.startsWith("172.16.")
                    || host.startsWith("192.168.")
                    || host.startsWith("169.254.");
        } catch (Exception e) {
            return true;
        }
    }
}