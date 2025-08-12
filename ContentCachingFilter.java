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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ContentCachingFilter extends OncePerRequestFilter {

  /**
   * 文字コード取得用正規表現（大小文字区別しない）.
   */
  private static final Pattern CHARSET_PATTERN = Pattern.compile("charset\\s*=\\s*([\\w-]+)", Pattern.CASE_INSENSITIVE);

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // リクエストとレスポンスのBodyをキャッシュする
    ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
    ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

    Instant start = Instant.now();

    // リクエストログ
    logRequest(requestWrapper);

    try {
      filterChain.doFilter(requestWrapper, responseWrapper);
    } finally {
      Instant end = Instant.now();
      Duration duration = Duration.between(start, end);

      // レスポンスログ
      logResponse(responseWrapper, duration);
      responseWrapper.copyBodyToResponse();
    }
  }

  @Override
  protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    // TODO 自動生成されたメソッド・スタブ
    super.doFilterNestedErrorDispatch(request, response, filterChain);
  }

  private void logRequest(ContentCachingRequestWrapper request) {
    System.out.println("=== 请求信息 ===");
//    request.getServletPath()
    System.out.println("URL: " + request.getRequestURL());
    System.out.println("Method: " + request.getMethod());

    String fullUrl = request.getRequestURL().toString() + getFullUrl(request);

    String mappingPath = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

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

    // Bodyの文字コードを取得する
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

  private void logResponse(ContentCachingResponseWrapper response, Duration duration) {
    System.out.println("=== 响应信息 ===");
    System.out.println("Status: " + response.getStatus());
    // 记录所有响应头（支持多值头）
    HttpHeaders headers = new HttpHeaders();
    response.getHeaderNames().forEach(
	headerName -> response.getHeaders(headerName).forEach(headerValue -> headers.add(headerName, headerValue)));
    System.out.println("All Headers: " + headers);

    // Bodyの文字コードを取得する
    String charset = getCharset(response.getContentType());

    // 读取缓存的响应 Body
    byte[] responseBody = response.getContentAsByteArray();
    if (responseBody.length > 0) {
      try {
	System.out.println("body: " + new String(responseBody, charset));
      } catch (UnsupportedEncodingException e) {
	// 処理なし
      }
    }
  }

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

  private String getFullUrl(ContentCachingRequestWrapper request) {
    String fullUrl = request.getRequestURL().toString();
    if (CheckUtils.isEmpty(request.getQueryString())) {
      fullUrl += "?" + URLDecoder.decode(request.getQueryString(), StandardCharsets.UTF_8);
    }

    return fullUrl;
  }
}
