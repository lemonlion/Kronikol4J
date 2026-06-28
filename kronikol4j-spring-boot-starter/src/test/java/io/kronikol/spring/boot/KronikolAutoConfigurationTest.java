package io.kronikol.spring.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

class KronikolAutoConfigurationTest {

    @Test
    void registersFilterAndRestTemplateCustomizerByDefault() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(KronikolAutoConfiguration.class);
            ctx.refresh();

            assertThat(ctx.getBean(KronikolProperties.class)).isNotNull();
            assertThat(ctx.containsBean("kronikolServletFilter")).isTrue();
            assertThat(ctx.getBean(RestTemplateCustomizer.class)).isNotNull();
            // auto-injection extends to the other framework-created clients (RestClient + reactive WebClient)
            assertThat(ctx.getBean(RestClientCustomizer.class)).isNotNull();
            assertThat(ctx.getBean(WebClientCustomizer.class)).isNotNull();
        }
    }

    @Test
    void customizersActuallyTrackTheBuiltClients() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(KronikolAutoConfiguration.class);
            ctx.refresh();

            // RestClient: the customizer adds our request interceptor
            RestClient.Builder restClient = RestClient.builder();
            ctx.getBean(RestClientCustomizer.class).customize(restClient);
            restClient.requestInterceptors(interceptors ->
                assertThat(interceptors).anySatisfy(i ->
                    assertThat(i).isInstanceOf(io.kronikol.spring.KronikolRestTemplateInterceptor.class)));

            // WebClient: the customizer adds our exchange filter
            WebClient.Builder webClient = WebClient.builder();
            ctx.getBean(WebClientCustomizer.class).customize(webClient);
            webClient.filters(filters ->
                assertThat(filters).anySatisfy(f ->
                    assertThat(f).isInstanceOf(io.kronikol.spring.KronikolWebClientFilter.class)));
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
    void registersHibernateStatementInspectorWhenHibernateOnClasspath() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            addProperty(ctx, "kronikol.service-name", "ShopDb");
            ctx.register(KronikolAutoConfiguration.class);
            ctx.refresh();

            var customizer = ctx.getBean(
                org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer.class);
            // Applying it installs a KronikolStatementInspector under Hibernate's statement-inspector setting.
            var hibernateProps = new java.util.HashMap<String, Object>();
            customizer.customize(hibernateProps);
            assertThat(hibernateProps.get(org.hibernate.cfg.AvailableSettings.STATEMENT_INSPECTOR))
                .isInstanceOf(io.kronikol.hibernate.KronikolStatementInspector.class);
        }
    }

    @Test
    void hibernateInspectorDisabledByProperty() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            addProperty(ctx, "kronikol.hibernate-tracking", "false");
            ctx.register(KronikolAutoConfiguration.class);
            ctx.refresh();

            assertThat(ctx.getBeanNamesForType(
                org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer.class)).isEmpty();
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
