在 Spring Boot 3 中，默认的异常响应 JSON 格式遵循了 **RFC 7807 Problem Details** 标准（即 `application/problem+json` 媒体类型），但做了一些简化。以下是默认的 JSON 结构及关键字段说明：

---

### **1. 默认 JSON 响应格式**
#### **通用错误示例（如 500 错误）**：
```json
{
  "timestamp": "2023-11-20T12:00:00.000+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "path": "/api/users"
}
```

#### **校验失败示例（400 错误）**：
```json
{
  "timestamp": "2023-11-20T12:01:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "path": "/api/users",
  "message": "Validation failed",
  "errors": [
    {
      "field": "email",
      "message": "必须是有效的电子邮件地址"
    }
  ]
}
```

---

### **2. 默认字段说明**
| 字段名        | 类型       | 说明                                                                 |
|--------------|------------|----------------------------------------------------------------------|
| `timestamp`  | `string`   | 错误发生的时间戳（ISO 8601 格式）                                    |
| `status`     | `number`   | HTTP 状态码（如 400、404、500）                                      |
| `error`      | `string`   | HTTP 状态文本（如 "Bad Request"、"Not Found"）                       |
| `path`       | `string`   | 请求的 API 路径（如 "/api/users"）                                   |
| `message`    | `string`   | 可读的错误描述（可能来自异常或校验注解的 `message` 属性）              |
| `errors`     | `array`    | **仅校验失败时存在**，包含字段级错误详情（`field` + `message`）       |

---

### **3. 配置控制字段可见性**
通过 `application.yml` 控制是否包含额外字段：
```yaml
server:
  error:
    include-message: always      # 是否包含 `message` 字段
    include-binding-errors: always  # 是否包含 `errors` 字段（校验失败时）
    include-stacktrace: never   # 是否包含堆栈信息（生产环境必须关闭）
    include-exception: false    # 是否包含异常类名（如 "NullPointerException"）
    include-path: always        # 是否包含 `path` 字段
```

---

### **4. 常见场景示例**
#### **404 资源不存在**
```json
{
  "timestamp": "2023-11-20T12:02:00.000+00:00",
  "status": 404,
  "error": "Not Found",
  "path": "/api/users/999",
  "message": "No handler found for GET /api/users/999"
}
```

#### **405 方法不支持**
```json
{
  "timestamp": "2023-11-20T12:03:00.000+00:00",
  "status": 405,
  "error": "Method Not Allowed",
  "path": "/api/users",
  "message": "Request method 'PUT' is not supported"
}
```

#### **500 服务器内部错误**
```json
{
  "timestamp": "2023-11-20T12:04:00.000+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "path": "/api/crash",
  "message": "Something went wrong"  # 生产环境建议关闭具体消息
}
```

---

### **5. 自定义默认格式**
如果想完全覆盖默认格式（例如改用 `code` 代替 `status`），需通过 `@ControllerAdvice` 实现：
```java
@RestControllerAdvice
public class CustomErrorHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex, WebRequest request) {
        Map<String, Object> body = Map.of(
            "code", HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "reason", "Server Error",
            "details", ex.getMessage()  // 生产环境应隐藏此字段
        );
        return ResponseEntity.internalServerError().body(body);
    }
}
```

---

### **6. 注意事项**
1. **生产环境安全**：
   - 确保 `include-stacktrace: never` 和 `include-exception: false`。
   - 避免在 `message` 中泄露敏感信息（如 SQL 错误）。

2. **一致性**：
   - 默认格式适用于大多数场景，如需扩展字段（如错误代码 `error_code`），建议统一通过 `@ControllerAdvice` 处理。

3. **RFC 7807 兼容性**：
   - 如需完全遵循 RFC 7807 标准（包含 `type` 和 `instance` 字段），需自定义实现。

---

通过以上配置和示例，你可以清晰掌握 Spring Boot 3 的默认异常响应格式，并根据需求灵活调整。