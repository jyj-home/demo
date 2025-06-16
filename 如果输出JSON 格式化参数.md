当你想在 Spring Boot 3 中**输出 JSON 格式化方法参数日志**时，如果参数中包含**字节流类型**（如 `InputStream`、`InputResource`、`MultipartFile`），不能直接序列化为 JSON，否则会导致：

* **异常**（如 `com.fasterxml.jackson.databind.JsonMappingException`）
* **读取消耗流**导致后续处理失败
* **内存问题**（文件过大被加载到内存）

---

### ✅ **正确做法：序列化时跳过或特殊处理流式类型参数**

你可以在使用 `Jackson` 序列化参数时，手动对这些敏感类型做标记或替代处理。

---

### ✅ 示例：AOP + Jackson JSON 序列化 + 特殊处理流类型

```java
@Aspect
@Component
public class JsonLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(JsonLoggingAspect.class);

    private final ObjectMapper objectMapper;

    public JsonLoggingAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Pointcut("execution(* com.example..*.*(..))")
    public void controllerMethods() {}

    @Before("controllerMethods()")
    public void logMethodArguments(JoinPoint joinPoint) throws JsonProcessingException {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = signature.getParameterNames();

        Map<String, Object> paramMap = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            String paramName = paramNames[i];

            // 特殊处理字节流参数
            if (arg instanceof InputStream) {
                paramMap.put(paramName, "<InputStream>");
            } else if (arg instanceof MultipartFile file) {
                paramMap.put(paramName, Map.of(
                        "filename", file.getOriginalFilename(),
                        "size", file.getSize(),
                        "contentType", file.getContentType()
                ));
            } else if (arg instanceof Resource resource) {
                paramMap.put(paramName, "<Resource: " + resource.getDescription() + ">");
            } else {
                paramMap.put(paramName, arg); // 可序列化对象
            }
        }

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(paramMap);
        logger.info("Method: {}.{}()\nParameters:\n{}", 
            signature.getDeclaringTypeName(), 
            signature.getMethod().getName(), 
            json);
    }
}
```

---

### ✅ 日志输出示例

```json
Method: com.example.MyController.uploadFile()
Parameters:
{
  "file": {
    "filename": "data.csv",
    "size": 1048576,
    "contentType": "text/csv"
  },
  "metadata": {
    "userId": 123,
    "description": "import data"
  }
}
```

---

### 🚫 不推荐的方式

```java
objectMapper.writeValueAsString(args); // ❌ 直接序列化全部参数，可能抛异常
```

---

### 🔐 小技巧（可选）

如果你使用 `Lombok` 的 `@ToString`，一定要加 `@ToString.Exclude` 到流字段上，否则也可能引发日志安全问题或性能问题。

---

### ✅ 总结

| 类型              | JSON 日志处理建议                        |
| --------------- | ---------------------------------- |
| `InputStream`   | 标记为 `<InputStream>`                |
| `MultipartFile` | 输出文件名、大小、类型等元数据                    |
| `Resource`      | 输出 description，如 `<Resource: ...>` |
| 普通对象/POJO       | 使用 `ObjectMapper` 正常序列化            |

---

如你还用到了 `@RequestBody` 的自动绑定参数，也可以单独处理。如果你希望我给出**全局日志过滤器或切面处理上传请求 + JSON 请求体的完整日志方案**，我可以继续补充。是否需要？
