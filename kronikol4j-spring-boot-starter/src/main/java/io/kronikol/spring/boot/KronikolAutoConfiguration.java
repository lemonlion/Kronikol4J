package io.kronikol.spring.boot;

import io.kronikol.http.HttpTrackingOptions;
import io.kronikol.servlet.KronikolServletFilter;
import io.kronikol.spring.KronikolRestTemplateInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configures Kronikol4J for Spring Boot (plan §7): a servlet filter that resolves test identity
 * from incoming request headers (Layer 1), and a {@link RestTemplateCustomizer} that tracks outgoing
 * HTTP calls. Active by default; disable with {@code kronikol.enabled=false}.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "kronikol", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KronikolProperties.class)
public class KronikolAutoConfiguration {

    /** Server-side: scope test identity from the incoming request's headers (Layer 1, §3.2/§7). */
    @Bean
    @ConditionalOnClass(jakarta.servlet.Filter.class)
    public FilterRegistrationBean<KronikolServletFilter> kronikolServletFilter() {
        FilterRegistrationBean<KronikolServletFilter> registration =
            new FilterRegistrationBean<>(new KronikolServletFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** Client-side: track outgoing RestTemplate calls. */
    @Bean
    @ConditionalOnClass(RestTemplate.class)
    public RestTemplateCustomizer kronikolRestTemplateCustomizer(KronikolProperties properties) {
        return restTemplate -> restTemplate.getInterceptors().add(
            new KronikolRestTemplateInterceptor(HttpTrackingOptions.forService(properties.getServiceName())));
    }
}
