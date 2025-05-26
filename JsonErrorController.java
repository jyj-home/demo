package demo.api.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

record ErrorResponse(long timestamp, int status, String error, String message, String path) {
}

@RestController
public class JsonErrorController implements ErrorController {

  @GetMapping("/error")
  @PostMapping("/error")
  @PutMapping("/error")
  @DeleteMapping("/error")
  @PatchMapping("/error")
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

    return new ResponseEntity<>(response, HttpStatus.valueOf(Integer.parseInt(status.toString())));
  }
}
