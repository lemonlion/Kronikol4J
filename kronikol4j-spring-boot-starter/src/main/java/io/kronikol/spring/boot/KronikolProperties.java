package io.kronikol.spring.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Kronikol4J Spring Boot starter (prefix {@code kronikol}).
 *
 * <pre>
 * kronikol:
 *   enabled: true
 *   service-name: OrderService
 * </pre>
 */
@ConfigurationProperties(prefix = "kronikol")
public class KronikolProperties {

    /** Whether Kronikol4J auto-configuration is active. */
    private boolean enabled = true;

    /** The diagram participant name for this service's outgoing HTTP calls. */
    private String serviceName = "Service";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
