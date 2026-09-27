package pe.axiz.payment.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PspClientConfiguration {

    @Bean
    RestClient pspRestClient(
            RestClient.Builder builder,
            @Value("${psp.base-url}") String baseUrl,
            @Value("${psp.connect-timeout:500ms}") Duration connectTimeout,
            @Value("${psp.read-timeout:5s}") Duration readTimeout) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        requestFactory.enableCompression(true);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
