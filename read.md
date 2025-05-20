# Logbook 3.7 版本的正确使用方法

在 Logbook 3.7 版本中，`HttpRequest.getBodyAsString()` 确实直接返回 `String` 而不是 `Optional<String>`。让我们根据这个实际情况提供正确的解决方案。

## 确认的方法签名

在 Logbook 3.7 中，相关方法定义为：
```java
public interface HttpRequest extends HttpMessage {
    String getBodyAsString() throws IOException;
    // 其他方法...
}
```

## 完整正确的实现方案

```java
import org.zalando.logbook.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class Logbook37DualLogSink implements Sink {
    
    private static final Logger basicLogger = LoggerFactory.getLogger("BASIC_LOGGER");
    private static final Logger bodyLogger = LoggerFactory.getLogger("BODY_LOGGER");

    @Override
    public void write(Precorrelation precorrelation, HttpRequest request) throws IOException {
        // 记录基本信息
        basicLogger.info("Request: {} {}", request.getMethod(), request.getRequestUri());
        basicLogger.info("Request Headers: {}", request.getHeaders());
        
        // 处理请求体
        try {
            String body = request.getBodyAsString();
            if (!body.isEmpty() && isJsonContent(request)) {
                bodyLogger.info("Request Body: {}", body);
            }
        } catch (IOException e) {
            basicLogger.error("Failed to read request body", e);
        }
    }

    @Override
    public void write(Correlation correlation, HttpRequest request, HttpResponse response) throws IOException {
        // 记录基本信息
        basicLogger.info("Response Status: {}", response.getStatus());
        basicLogger.info("Response Headers: {}", response.getHeaders());
        basicLogger.info("Response Time: {} ms", correlation.getDuration().toMillis());
        
        // 处理响应体
        try {
            String body = response.getBodyAsString();
            if (!body.isEmpty() && isJsonContent(response)) {
                bodyLogger.info("Response Body: {}", body);
            }
        } catch (IOException e) {
            basicLogger.error("Failed to read response body", e);
        }
    }
    
    private boolean isJsonContent(HttpMessage message) {
        return message.getContentType()
                .map(ct -> ct.contains("application/json"))
                .orElse(false);
    }
}
```

## 关键点说明

1. **直接使用 String 返回值**：
   - 不再使用 Optional
   - 直接调用 `getBodyAsString()` 获取字符串

2. **空内容检查**：
   - 使用 `!body.isEmpty()` 检查空内容
   - 空字符串表示没有请求体或响应体

3. **异常处理**：
   - 添加 try-catch 块处理可能的 IOException
   - 记录错误日志以便排查问题

4. **内容类型检查**：
   - 保持 JSON 内容过滤逻辑
   - 只记录 application/json 类型的内容

## 为什么之前会出现混淆？

Logbook 的不同版本和不同方法之间存在差异：

1. **Logbook 2.x**：
   - `getBodyAsString()` 返回 String
   - `getBody()` 返回 byte[]

2. **Logbook 3.x**：
   - 保持了相同的返回类型
   - 主要改进在性能和内部实现上

## 生产环境建议

1. **性能考虑**：
   ```java
   // 对于大请求体，考虑限制日志长度
   if (body.length() > 1024) {
       bodyLogger.info("Request Body (truncated): {}", body.substring(0, 1024) + "...");
   } else {
       bodyLogger.info("Request Body: {}", body);
   }
   ```

2. **敏感信息处理**：
   ```java
   // 对敏感字段进行脱敏
   String sanitizedBody = body.replaceAll("\"password\":\"[^\"]*\"", "\"password\":\"*****\"");
   bodyLogger.info("Request Body: {}", sanitizedBody);
   ```

3. **异步记录**：
   考虑使用异步日志记录器（如 Logback 的 AsyncAppender）来减少对主业务逻辑的影响

这样实现的日志记录器将完全兼容 Logbook 3.7 版本，并能正确地将基本信息和消息体分别记录到不同的日志文件中。