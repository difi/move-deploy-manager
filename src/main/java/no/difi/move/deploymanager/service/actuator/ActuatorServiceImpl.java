package no.difi.move.deploymanager.service.actuator;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import no.difi.move.deploymanager.config.DeployManagerProperties;
import no.difi.move.deploymanager.domain.HealthStatus;
import no.difi.move.deploymanager.domain.VersionInfo;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ActuatorServiceImpl implements ActuatorService {

    private final DeployManagerProperties properties;
    private final ActuatorClient actuatorClient;

    public ActuatorServiceImpl(DeployManagerProperties properties, ActuatorClient actuatorClient) {
        this.properties = properties;
        this.actuatorClient = actuatorClient;
    }

    @Override
    public HealthStatus getStatus() {
        log.info("Performing health check");
        return actuatorClient.getStatus();
    }

    @Override
    @SneakyThrows(InterruptedException.class)
    public boolean shutdown() {
        log.trace("Calling ActuatorServiceImpl.shutdown()");
        if (!actuatorClient.requestShutdown()) {
            return getStatus() != HealthStatus.UP;
        }

        int shutdownRetries = properties.getShutdownRetries();
        int pollIntervalInMs = properties.getShutdownPollIntervalInMs();
        log.debug("Retries shutdown {} times with interval {}", shutdownRetries, pollIntervalInMs);
        for (int retries = shutdownRetries; retries > 0; --retries) {
            Thread.sleep(pollIntervalInMs);

            HealthStatus status = getStatus();
            log.info("Health status is {}", status);

            if (status != HealthStatus.UP) {
                return true;
            }
        }

        log.warn("Could not shutdown application");

        return false;
    }

    @Override
    public VersionInfo getVersionInfo() {
        return actuatorClient.getVersionInfo();
    }
}
