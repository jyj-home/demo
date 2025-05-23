package demo.api.log;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.Logbook;

@Configuration
public class LogbookConfig {

  @Bean
  public Logbook logbook(DualLogSink sink) {
    return Logbook.builder().sink(sink).build();
  }
}
