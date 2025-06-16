在 Spring Boot 3 中记录方法参数日志时，如果方法中包含像 `InputStream`、`InputResource`、`MultipartFile` 等字节流类型的参数，需要特别处理这些类型，否则直接输出可能会引发异常、占用大量内存，或导致请求失败。

以下是处理这类参数时的建议方法：

---

### ✅ **推荐做法：忽略或简要记录字节流类参数**

#### 示例代码（使用 AOP 实现日志记录）：

```java
@Aspect
@Component
public class MethodLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(MethodLogAspect.class);

    @Pointcut("execution(* com.example..*.*(..))") // 替换为你的包路径
    public void logPointcut() {}

    @Before("logPointcut()")
    public void logMethodArgs(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] parameterNames = signature.getParameterNames();

        StringBuilder sb = new StringBuilder();
        sb.append("Method: ").append(method.getName()).append(", Args: [");

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            String paramName = parameterNames[i];

            if (arg instanceof InputStream
                    || arg instanceof MultipartFile
                    || arg instanceof Resource) {
                sb.append(paramName).append("= <binary data>, ");
            } else {
                sb.append(paramName).append("=").append(safeToString(arg)).append(", ");
            }
        }

        if (args.length > 0) {
            sb.setLength(sb.length() - 2); // 去掉最后的逗号和空格
        }
        sb.append("]");

        logger.info(sb.toString());
    }

    private String safeToString(Object obj) {
        try {
            return String.valueOf(obj);
        } catch (Exception e) {
            return "<error converting to string>";
        }
    }
}
```

---

### 🚫 **不要直接尝试输出以下内容：**

* `InputStream`: 读取会消耗流，导致后续无法使用。
* `MultipartFile`: 如果尝试 `getBytes()` 会读取整个文件到内存，极易 OOM。
* `Resource` / `InputStreamSource`: 类似风险。

---

### 💡 **更好的做法（进阶）**

* **记录文件名、大小**（对 `MultipartFile`）：

  ```java
  if (arg instanceof MultipartFile file) {
      sb.append(paramName).append("= MultipartFile(name=")
        .append(file.getOriginalFilename()).append(", size=")
        .append(file.getSize()).append("), ");
  }
  ```

* **对 JSON 请求体做格式化记录（如 Jackson 自动序列化）**：
  只对非流参数使用 `ObjectMapper.writeValueAsString()`。

---

### ✅ 小结

| 参数类型                 | 建议做法                       |
| -------------------- | -------------------------- |
| InputStream          | 标记为 `<binary data>`，不读取    |
| MultipartFile        | 输出文件名、大小                   |
| Resource/InputSource | 输出类名或简要描述                  |
| 普通对象/POJO            | 可用 `toString()` 或 JSON 格式化 |

---

如你需要，我还可以给出带 JSON 格式化参数的完整 AOP 日志示例。是否需要？
