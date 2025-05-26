# Spring Boot 3 白板错误处理（返回JSON）

在Spring Boot 3中，"白板"通常指的是默认的WhiteLabel错误页面。当没有匹配的URL时，默认会显示这个HTML错误页面。以下是完全禁用白板错误页面并返回JSON响应的几种方法：

## 1. 完全禁用WhiteLabel错误页面

在`application.properties`或`application.yml`中：

```properties
server.error.whitelabel.enabled=false
```

## 2. 自定义错误控制器（推荐）

这是最完整的解决方案，可以完全控制错误响应格式：

```java
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
public class JsonErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR.value();
            message = "Unknown error occurred";
        }
        
        response.put("timestamp", System.currentTimeMillis());
        response.put("status", status);
        response.put("error", HttpStatus.valueOf(Integer.parseInt(status.toString())).getReasonPhrase());
        response.put("message", message != null ? message : "No message available");
        response.put("path", path != null ? path : "Unknown path");
        
        return new ResponseEntity<>(
            response, 
            HttpStatus.valueOf(Integer.parseInt(status.toString()))
        );
    }
}
```

## 3. 结合异常处理器

```java
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(NoHandlerFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", System.currentTimeMillis());
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", "Not Found");
        body.put("message", "Endpoint " + ex.getRequestURL() + " not found");
        body.put("path", ex.getRequestURL());
        
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }
}
```

## 4. 必要配置

在`application.properties`中需要添加：

```properties
# 确保抛出NoHandlerFoundException
spring.mvc.throw-exception-if-no-handler-found=true
# 禁用静态资源映射
spring.web.resources.add-mappings=false
# 禁用白板错误页面
server.error.whitelabel.enabled=false
```

## 5. 响应示例

当访问不存在的端点时，将返回如下JSON：

```json
{
    "timestamp": 1684137600000,
    "status": 404,
    "error": "Not Found",
    "message": "Endpoint /api/nonexistent not found",
    "path": "/api/nonexistent"
}
```

## 6. 进阶：统一响应格式

可以创建一个统一的错误响应结构：

```java
public record ErrorResponse(
    long timestamp,
    int status,
    String error,
    String message,
    String path
) {}

@RestController
public class JsonErrorController implements ErrorController {
    
    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        // ...获取错误信息...
        return new ResponseEntity<>(
            new ErrorResponse(
                System.currentTimeMillis(),
                statusCode,
                HttpStatus.valueOf(statusCode).getReasonPhrase(),
                errorMessage,
                path
            ),
            HttpStatus.valueOf(statusCode)
        );
    }
}
```

这样配置后，你的Spring Boot 3应用将不再显示白板错误页面，而是对所有未匹配的URL返回结构化的JSON错误响应。