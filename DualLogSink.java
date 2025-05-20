package demo.api.log;

import java.io.IOException;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpLogFormatter;
import org.zalando.logbook.HttpMessage;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.Sink;

@Component
public class DualLogSink implements Sink {
  private static final Logger basicLogger = LoggerFactory.getLogger("BASIC_LOGGER");
  private static final Logger bodyLogger = LoggerFactory.getLogger("BODY_LOGGER");

  private final HttpLogFormatter formatter;

  // 注入自动配置的格式化器
  public DualLogSink(HttpLogFormatter formatter) {
    this.formatter = formatter;
  }

  @Override
  public void write(@Nonnull Precorrelation precorrelation, @Nonnull HttpRequest request) throws IOException {
    // 1. 使用Logbook格式化器记录完整请求信息
    String formattedRequest = formatter.format(precorrelation, request);
    basicLogger.info("Formatted Request:\n{}", formattedRequest);

    // 2. 单独记录请求体
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
    // 1. 使用Logbook格式化器记录完整响应信息
    String formattedResponse = formatter.format(correlation, response);
    basicLogger.info("Formatted Response:\n{}", formattedResponse);

    // 2. 单独记录响应体
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
    String contentType = message.getContentType();
    return contentType != null && contentType.contains("application/json");
  }
}