# Spring Boot 中哪些错误会发送到 `/error` 端点

在 Spring Boot 应用中，多种类型的错误和异常最终都会被路由到 `/error` 端点处理。以下是会被发送到 `/error` 的主要错误类型：

## 1. HTTP 错误

- **404 Not Found** - 请求的 URL 没有对应的处理器
- **405 Method Not Allowed** - 请求方法不支持
- **415 Unsupported Media Type** - 不支持的媒体类型
- **500 Internal Server Error** - 服务器内部错误

## 2. 未捕获的异常

- 所有未被 `@ExceptionHandler` 捕获的异常
- `ServletException` 及其子类
- `IOException` 等 I/O 相关异常

## 3. Spring MVC 特定错误

- `NoHandlerFoundException` - 没有找到处理器 (404)
- `HttpRequestMethodNotSupportedException` - 方法不支持 (405)
- `HttpMediaTypeNotSupportedException` - 媒体类型不支持 (415)
- `MissingServletRequestParameterException` - 缺少请求参数 (400)
- `TypeMismatchException` - 类型不匹配 (400)

## 4. 自定义错误

- 通过 `response.sendError()` 手动触发的错误
- 通过 `throw new ResponseStatusException()` 抛出的错误

## 错误处理流程

1. 当错误发生时，Spring Boot 会尝试以下处理顺序：
   - 首先查找匹配的 `@ExceptionHandler`
   - 如果没有找到，错误会被转发到 `/error` 端点
   - `/error` 端点可以由 `ErrorController` 实现处理

2. 默认情况下，Spring Boot 提供了 `BasicErrorController` 处理这些错误

## 配置建议

```properties
# 确保404错误抛出异常而不是直接响应
spring.mvc.throw-exception-if-no-handler-found=true

# 禁用静态资源处理器的自动映射
spring.web.resources.add-mappings=false

# 禁用默认的WhiteLabel错误页面
server.error.whitelabel.enabled=false
```

## 自定义错误处理示例

```java
@RestController
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        Integer status = (Integer) request.getAttribute("javax.servlet.error.status_code");
        String path = (String) request.getAttribute("javax.servlet.error.request_uri");
        
        HttpStatus httpStatus = HttpStatus.valueOf(status != null ? status : 500);
        
        return new ResponseEntity<>(
            new ErrorResponse(
                httpStatus.value(),
                httpStatus.getReasonPhrase(),
                "Error occurred at " + path,
                System.currentTimeMillis()
            ),
            httpStatus
        );
    }
}

record ErrorResponse(int status, String error, String message, long timestamp) {}
```

这样配置后，所有未被专门处理的错误都会通过这个统一的错误处理器返回结构化的 JSON 响应。