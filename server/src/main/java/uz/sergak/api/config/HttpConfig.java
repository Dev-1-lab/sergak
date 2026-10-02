package uz.sergak.api.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Clock;

@Configuration
public class HttpConfig {

    /** Tashqi provayderlar sekin javob bersa, ilova kutib qolmasligi uchun qisqa timeoutlar. */
    @Bean
    RestClientCustomizer timeouts() {
        return builder -> {
            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(4000);
            f.setReadTimeout(8000);
            builder.requestFactory(f);
        };
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
