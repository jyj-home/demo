Jackson **本身不会自动处理流类型（如 `InputStream`、`MultipartFile`、`Resource`）**，因为这些类型**不可安全或合理地序列化为 JSON**，原因如下：

---

### ❗为什么 Jackson 不自动处理这些类型？

| 类型                  | 原因                                     |
| ------------------- | -------------------------------------- |
| `InputStream`       | 是流，无法重读，Jackson 读取它会消耗内容，后续无法再用        |
| `MultipartFile`     | 内容通常为大文件，直接序列化会导致内存暴涨或 OOM             |
| `Resource`          | 是抽象资源引用（可能是文件、URL、classpath 等），无内容可序列化 |
| `InputStreamSource` | Jackson 无法解析其内部结构，默认抛异常                |

因此 Jackson 遇到这些类型时，**默认行为是抛出 `JsonMappingException` 或序列化为空对象**，以避免不安全操作。

---

### ✅ 可选的自动处理方式：注册 **`MixIn` 或自定义序列化器**

你可以通过\*\*自定义序列化器（`JsonSerializer`）\*\*告诉 Jackson 如何安全地“表示”这些类型，比如只记录它们的类型或描述信息。

---

### ✅ 示例：自定义 `MultipartFile` 序列化器

```java
public class MultipartFileSerializer extends JsonSerializer<MultipartFile> {
    @Override
    public void serialize(MultipartFile file, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        gen.writeStartObject();
        gen.writeStringField("filename", file.getOriginalFilename());
        gen.writeNumberField("size", file.getSize());
        gen.writeStringField("contentType", file.getContentType());
        gen.writeEndObject();
    }
}
```

然后注册这个序列化器：

```java
@Bean
public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addSerializer(MultipartFile.class, new MultipartFileSerializer());
    mapper.registerModule(module);
    return mapper;
}
```

你也可以为 `InputStream` 和 `Resource` 做类似处理。

---

### ✅ 或：忽略不可序列化字段（使用 Jackson 注解）

如果你只关心对象里其他字段，可以：

```java
@JsonIgnoreProperties({"inputStream", "resource"})
public class MyDTO {
    private String name;
    private InputStream inputStream;
    private Resource resource;
}
```

---

### ✅ 或：写一个安全包装器

如果你想记录方法参数日志，而又不想影响原始对象，可以在 AOP 里用一个方法把参数转换为“安全版”对象，用于日志记录。

---

### 总结：Jackson 自动行为 vs 自定义行为

| 行为            | 说明                      |
| ------------- | ----------------------- |
| 默认 Jackson 行为 | 不会序列化流类型，遇到会报错或输出空值     |
| 自定义序列化器       | 可输出文件名、大小等安全信息          |
| 使用 MixIn 或注解  | 可忽略不可序列化字段，或只序列化部分字段    |
| 建议方式          | 在日志中手动处理流类型字段或注册自定义序列化器 |

---

如你希望我提供完整的 Jackson 模块封装（可直接注册所有流类型的序列化器），我可以打包一个例子给你。是否需要？
