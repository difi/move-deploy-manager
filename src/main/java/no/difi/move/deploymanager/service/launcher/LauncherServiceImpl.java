package no.difi.move.deploymanager.service.launcher;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import no.difi.move.deploymanager.config.DeployManagerProperties;
import no.difi.move.deploymanager.domain.HealthStatus;
import no.difi.move.deploymanager.service.actuator.ActuatorService;
import no.difi.move.deploymanager.service.launcher.dto.LaunchResult;
import no.difi.move.deploymanager.service.launcher.dto.LaunchStatus;
import org.springframework.stereotype.Service;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Future;

@Service
@Slf4j
public class LauncherServiceImpl implements LauncherService {

    private final DeployManagerProperties properties;
    private final ActuatorService actuatorService;

    public LauncherServiceImpl(DeployManagerProperties properties, ActuatorService actuatorService) {
        this.properties = properties;
        this.actuatorService = actuatorService;
    }

    @Override
    public LaunchResult launchIntegrasjonspunkt(String jarPath) {
        LaunchResult launchResult = launch(jarPath);
        HealthStatus status = actuatorService.getStatus();

        log.info("Status is {}", status);

        if (status != HealthStatus.UP) {
            launchResult.setStatus(LaunchStatus.FAILED);
        }

        return launchResult;
    }

    @SneakyThrows(InterruptedException.class)
    private LaunchResult launch(String jarPath) {
        LaunchResult launchResult = new LaunchResult()
                .setJarPath(jarPath);

        try (StartupLog startupLog = new StartupLog(properties.isVerbose())) {
            log.info("Starting application {}", jarPath);

            Future<ProcessResult> future = new ProcessExecutor(Arrays.asList(
                    "java", "-jar", jarPath,
                    "--management.endpoint.shutdown.enabled=true",
                    "--spring.profiles.active=" + properties.getIntegrasjonspunkt().getProfile()))
                    .directory(new File(properties.getRoot()))
                    .redirectOutput(startupLog)
                    .start()
                    .getFuture();

            switch (waitForStartup(startupLog)) {
                case SUCCESS:
                    log.info("Application started successfully!");
                    break;
                case FAILED:
                    log.error("Application failed!");
                    future.cancel(true);
                    break;
                case UNKNOWN:
                    log.warn("A timeout occurred!");
                    future.cancel(true);
                    break;
                default:
                    break;
            }

            launchResult
                    .setStatus(startupLog.getStatus())
                    .setStartupLog(startupLog.getLog());
        } catch (IOException e) {
            log.error("Failed to launch process.", e);
            launchResult
                    .setStatus(LaunchStatus.FAILED)
                    .setStartupLog(e.getLocalizedMessage());
        }

        return launchResult;
    }

    private LaunchStatus waitForStartup(StartupLog startupLog) throws InterruptedException {
        long start = System.currentTimeMillis();

        do {
            Thread.sleep(properties.getLaunchPollIntervalInMs());
        } while (startupLog.getStatus() == LaunchStatus.UNKNOWN
                && System.currentTimeMillis() - start < properties.getLaunchTimeoutInMs()
        );

        startupLog.stopRecording();

        return startupLog.getStatus();
    }
}
