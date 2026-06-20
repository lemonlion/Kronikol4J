package io.kronikol.spring.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.PropertiesPropertySource;

class KronikolAutoConfigurationTest {

    @Test
    void registersFilterAndRestTemplateCustomizerByDefault() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(KronikolAutoConfiguration.class);
            ctx.refresh();

            assertThat(ctx.getBean(KronikolProperties.class)).isNotNull();
            assertThat(ctx.containsBean("kronikolServletFilter")).isTrue();
            assertThat(ctx.getBean(RestTemplateCustomizer.class)).isNotNull();
        }
    }

    @Test
    void disabledWhenPropertyFalse() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            addProperty(ctx, "kronikol.enabled", "false");
            ctx.register(KronikolAutoConfiguration.class);
            ctx.refresh();

            assertThat(ctx.getBeanNamesForType(KronikolProperties.class)).isEmpty();
        }
    }

    @Test
    void serviceNameBindsFromConfiguration() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            addProperty(ctx, "kronikol.service-name", "OrderService");
            ctx.register(KronikolAutoConfiguration.class);
            ctx.refresh();

            assertThat(ctx.getBean(KronikolProperties.class).getServiceName()).isEqualTo("OrderService");
        }
    }

    private static void addProperty(AnnotationConfigApplicationContext ctx, String key, String value) {
        Properties properties = new Properties();
        properties.setProperty(key, value);
        ctx.getEnvironment().getPropertySources().addFirst(new PropertiesPropertySource("test", properties));
    }
}
