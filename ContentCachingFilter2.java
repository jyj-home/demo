package demo.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo.api.utils.CheckUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

//@Component
//@Order(Ordered.HIGHEST_PRECEDENCE)
public class ContentCachingFilter2 extends OncePerRequestFilter {

  // 正则表达式模式，忽略大小写
  private static final Pattern CHARSET_PATTERN = Pattern.compile("charset\\s*=\\s*([\\w-]+)", Pattern.CASE_INSENSITIVE);

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // 包装请求和响应以缓存 Body
    ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
    ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

    // 记录请求和响应信息
    logRequest(requestWrapper);

    try {

      filterChain.doFilter(requestWrapper, responseWrapper);
    } finally {
      logResponse(responseWrapper);
      // 必须将缓存的响应 Body 写回客户端
      responseWrapper.copyBodyToResponse();
    }
  }

  private void logRequest(ContentCachingRequestWrapper request) {
    System.out.println("=== 请求信息 ===");
//    request.getServletPath()
    System.out.println("URL: " + request.getRequestURL());
    System.out.println("Method: " + request.getMethod());

    // 获取所有请求头并转为 Map
    Map<String, String> headers = Collections.list(request.getHeaderNames()).stream()
	.collect(Collectors.toMap(headerName -> headerName, request::getHeader));

//    System.out.println("Headers: "
//	+ Collections.list(request.getHeaderNames()).stream().collect(Collectors.toMap(h -> h, request::getHeader)));
    try {
      System.out.println("Headers: " + objectMapper.writeValueAsString(headers));
    } catch (JsonProcessingException e) {
      // TODO 自動生成された catch ブロック
      e.printStackTrace();
    }

//  if (requestBody.contains("password")) {
//  requestBody = requestBody.replaceAll("\"password\":\".*?\"", "\"password\":\"***\"");
//}

    // 读取缓存的请求 Body
    String charset = getCharset(request.getContentType());

    byte[] requestBody = request.getContentAsByteArray();
    if (requestBody.length > 0) {
      try {
	System.out.println("Body: " + new String(requestBody, charset));
      } catch (UnsupportedEncodingException e) {
	// 処理なし
      }
    }
  }

  private void logResponse(ContentCachingResponseWrapper response) {
    System.out.println("=== 响应信息 ===");
    System.out.println("Status: " + response.getStatus());
    // 记录所有响应头（支持多值头）
    HttpHeaders headers = new HttpHeaders();
    response.getHeaderNames().forEach(
	headerName -> response.getHeaders(headerName).forEach(headerValue -> headers.add(headerName, headerValue)));
    System.out.println("All Headers: " + headers);

    // 读取缓存的响应 Body
    byte[] responseBody = response.getContentAsByteArray();
    if (responseBody.length > 0) {
      try {
	System.out.println("body: " + new String(responseBody, response.getCharacterEncoding()));
      } catch (UnsupportedEncodingException e) {
	// TODO 自動生成された catch ブロック
	e.printStackTrace();
      }
    }
  }

//  public static void main(String[] args) {
//    // 测试用例
//    String[] testCases = { "text/html; charset =   utf-8", "application/json;charset =  GBK", "text/plain",
//	"image/png; charset  =ISO-8859-1", "application/xml; Charset=   ", "text/css;charset =utf-16le" };
//
//    for (String contentType : testCases) {
//      String charset = getCharset(contentType);
//      System.out.printf("Content-Type: %-30s -> Charset: %s%n", contentType, charset);
//    }
//  }

  /**
   * @param contentType コンテンツタイプ
   * @return 文字コード
   */
  private String getCharset(String contentType) {

    String charset = StandardCharsets.UTF_8.toString();

    if (CheckUtils.isEmpty(contentType)) {
      return charset;
    }

    Matcher matcher = CHARSET_PATTERN.matcher(contentType);
    if (matcher.find()) {
      charset = matcher.group(1).trim();
    }
    return charset;
  }
}
