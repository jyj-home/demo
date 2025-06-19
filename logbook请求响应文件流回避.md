在 Spring Boot 中使用 Logbook 记录请求/响应时，如果遇到**文件流（`InputStream`/`OutputStream`）报错**，可以通过以下方案在**不破坏默认日志逻辑**的前提下优雅回避该问题：

---

### **1. 原因分析**
Logbook 默认的 `BodyFilter` 和 `HttpLogWriter` 假设请求/响应体是可重复读取的字符串或字节数组，但文件流：
1. **不可重复读取**（读取后流会关闭）
2. **可能占用系统资源**（如大文件上传/下载）
3. **无意义的日志内容**（二进制文件流直接转字符串是乱码）

---

### **2. 解决方案**
#### **方案一：动态跳过文件流请求（推荐）**
通过 `Predicate<HttpRequest>` 排除文件流请求，不记录其 body：
```java
@Bean
public Logbook logbook() {
    return Logbook.builder()
        .requestFilter(request -> {
            // 如果 Content-Type 是文件类型（如 multipart/form-data、application/octet-stream），跳过 body 记录
            return request.getContentType()
                .map(type -> !type.startsWith("multipart/") && !type.equals("application/octet-stream"))
                .orElse(true);
        })
        .responseFilter(response -> {
            // 同理，跳过文件类型的响应
            return response.getContentType()
                .map(type -> !type.startsWith("multipart/") && !type.equals("application/octet-stream"))
                .orElse(true);
        })
        .build();
}
```
**效果**：  
- 文件上传/下载请求仍会被记录，但 body 显示为 `[omitted]`  
- 普通请求的日志不受影响

---

#### **方案二：自定义 `BodyFilter` 安全处理流**
对文件流返回占位符，避免解析：
```java
@Bean
public BodyFilter fileStreamAwareBodyFilter() {
    return (contentType, body) -> {
        if (contentType == null) {
            return body;
        }
        // 如果是文件流类型，返回占位符
        if (contentType.startsWith("multipart/") || contentType.equals("application/octet-stream")) {
            return "[file-stream-omitted]";
        }
        return body; // 其他情况正常处理
    };
}
```
**配置生效**：
```yaml
# application.yml
logbook:
  body-filter:
    enabled: true
```

---

#### **方案三：扩展 `Sink` 跳过文件流日志**
完全自定义日志输出逻辑：
```java
@Bean
public Sink fileStreamSafeSink(JsonHttpLogFormatter formatter) {
    return new DefaultSink(formatter, new HttpLogWriter() {
        @Override
        public void write(String message) throws IOException {
            // 如果日志包含文件流标记，则跳过或简化输出
            if (message.contains("multipart/form-data") || message.contains("application/octet-stream")) {
                logger.debug("File stream request/response omitted");
            } else {
                logger.info(message);
            }
        }
    });
}
```

---

### **3. 补充优化**
#### **3.1 确保缓存过滤器优先执行**
如果仍有流关闭问题，强制缓存非文件请求：
```java
@Bean
@Order(Ordered.HIGHEST_PRECEDENCE)
public Filter cachingFilter() {
    return new CachingFilter(request -> {
        // 只缓存非文件请求
        return request.getContentType()
            .map(type -> !type.startsWith("multipart/"))
            .orElse(true);
    });
}
```

#### **3.2 配置 Logbook 策略**
```yaml
# application.yml
logbook:
  strategy: without-body  # 默认不记录 body，特殊请求再通过 Predicate 放行
  obfuscate:
    headers: ["Authorization"]  # 始终脱敏敏感头
```

---

### **4. 效果对比**
| **方案**               | **优点**                          | **缺点**                          |
|------------------------|----------------------------------|----------------------------------|
| 动态跳过文件流（方案一） | 轻量级，不影响性能               | 需要明确识别文件类型             |
| 自定义 BodyFilter（方案二） | 灵活控制输出内容                | 需手动维护 Content-Type 判断逻辑 |
| 扩展 Sink（方案三）     | 完全控制日志输出方式            | 实现复杂度较高                   |

---

### **5. 最终推荐方案**
**组合使用方案一 + 方案二**：
1. 用 `Predicate` 跳过文件流请求的 body 记录  
2. 用 `BodyFilter` 对漏网的流请求返回占位符  
3. （可选）添加缓存过滤器保护普通请求  

```java
@Configuration
public class LogbookSafeStreamConfig {

    @Bean
    public Logbook logbook() {
        return Logbook.builder()
            .requestFilter(this::skipFileStreamRequests)
            .responseFilter(this::skipFileStreamResponses)
            .bodyFilter(fileStreamAwareBodyFilter())
            .build();
    }

    private boolean skipFileStreamRequests(HttpRequest request) {
        return request.getContentType()
            .map(type -> !isFileStreamContentType(type))
            .orElse(true);
    }

    private boolean skipFileStreamResponses(HttpResponse response) {
        return response.getContentType()
            .map(type -> !isFileStreamContentType(type))
            .orElse(true);
    }

    private boolean isFileStreamContentType(String contentType) {
        return contentType.startsWith("multipart/") 
            || contentType.equals("application/octet-stream");
    }

    @Bean
    public BodyFilter fileStreamAwareBodyFilter() {
        return (contentType, body) -> 
            contentType != null && isFileStreamContentType(contentType) 
                ? "[file-stream-omitted]" 
                : body;
    }
}
```

**此方案确保**：  
✅ 文件流请求/响应不会触发流解析  
✅ 普通请求的日志完整记录  
✅ 无需修改 Logbook 默认逻辑