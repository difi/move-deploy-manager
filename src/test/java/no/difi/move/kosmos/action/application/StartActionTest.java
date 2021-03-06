package no.difi.move.kosmos.action.application;

import no.difi.move.kosmos.config.BlocklistProperties;
import no.difi.move.kosmos.config.KosmosProperties;
import no.difi.move.kosmos.domain.HealthStatus;
import no.difi.move.kosmos.domain.application.Application;
import no.difi.move.kosmos.domain.application.ApplicationMetadata;
import no.difi.move.kosmos.repo.KosmosDirectoryRepo;
import no.difi.move.kosmos.service.actuator.ActuatorService;
import no.difi.move.kosmos.service.launcher.LauncherService;
import no.difi.move.kosmos.service.launcher.dto.LaunchResult;
import no.difi.move.kosmos.service.launcher.dto.LaunchStatus;
import no.difi.move.kosmos.service.mail.MailService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class StartActionTest {

    @InjectMocks
    private StartAction target;

    @Mock
    private KosmosProperties propertiesMock;
    @Mock
    private ActuatorService actuatorServiceMock;
    @Mock
    private LauncherService launcherServiceMock;
    @Mock
    private KosmosDirectoryRepo kosmosDirectoryRepoMock;
    @Mock
    private MailService mailService;
    @Mock
    private Application applicationMock;
    @Mock
    private ApplicationMetadata latestMock;
    @Mock
    private File fileMock;
    @Mock
    private BlocklistProperties blocklistProperties;

    @Before
    public void before() {
        given(blocklistProperties.isEnabled()).willReturn(true);
        given(propertiesMock.getBlocklist()).willReturn(blocklistProperties);
        given(applicationMock.getLatest()).willReturn(latestMock);
        given(latestMock.getFile()).willReturn(fileMock);
        given(fileMock.getName()).willReturn("test.jar");
        given(launcherServiceMock.launchIntegrasjonspunkt(any())).willReturn(
                new LaunchResult()
                        .setStatus(LaunchStatus.SUCCESS)
                        .setStartupLog("theStartupLog")
        );
    }

    @Test(expected = NullPointerException.class)
    public void apply_toNull_shouldThrow() {
        target.apply(null);
    }

    @Test
    public void apply_currentVersionIsLatestAndHealthStatusIsUp_shouldNotStart() {
        given(applicationMock.getCurrent())
                .willReturn(new ApplicationMetadata().setVersion("latest"));
        given(applicationMock.isSameVersion()).willReturn(true);
        given(actuatorServiceMock.getStatus()).willReturn(HealthStatus.UP);
        assertThat(target.apply(applicationMock)).isSameAs(applicationMock);
        verify(launcherServiceMock, never()).launchIntegrasjonspunkt(anyString());
    }

    @Test
    public void apply_currentVersionIsLatestAndHealthStatusIsDown_shouldStart() {
        given(applicationMock.getCurrent())
                .willReturn(new ApplicationMetadata().setVersion("latest"));
        given(applicationMock.isSameVersion()).willReturn(true);
        given(actuatorServiceMock.getStatus()).willReturn(HealthStatus.DOWN);
        given(fileMock.getAbsolutePath()).willReturn("the path");
        assertThat(target.apply(applicationMock)).isSameAs(applicationMock);
        verify(launcherServiceMock).launchIntegrasjonspunkt("the path");
        verify(mailService).sendMail("Upgrade SUCCESS test.jar", "theStartupLog");
    }

    @Test
    public void apply_currentVersionIsOldAndHealthStatusIsUp_shouldStart() {
        given(applicationMock.getCurrent())
                .willReturn(new ApplicationMetadata().setVersion("old"));
        given(applicationMock.isSameVersion()).willReturn(false);
        given(fileMock.getAbsolutePath()).willReturn("the path");
        assertThat(target.apply(applicationMock)).isSameAs(applicationMock);
        verify(launcherServiceMock).launchIntegrasjonspunkt("the path");
        verify(mailService).sendMail("Upgrade SUCCESS test.jar", "theStartupLog");
    }

    @Test
    public void apply_whenStartFails_theJarFileShouldBeBlacklisted() {
        given(launcherServiceMock.launchIntegrasjonspunkt(any())).willReturn(
                new LaunchResult()
                        .setStatus(LaunchStatus.FAILED)
                        .setStartupLog("theStartupLog")
        );

        given(applicationMock.getCurrent())
                .willReturn(new ApplicationMetadata().setVersion("latest"));
        given(applicationMock.isSameVersion()).willReturn(true);
        given(actuatorServiceMock.getStatus()).willReturn(HealthStatus.DOWN);
        given(fileMock.getAbsolutePath()).willReturn("the path");
        assertThat(target.apply(applicationMock)).isSameAs(applicationMock);
        verify(launcherServiceMock).launchIntegrasjonspunkt("the path");
        verify(mailService).sendMail("Upgrade FAILED test.jar", "theStartupLog");
        verify(kosmosDirectoryRepoMock).blockList(fileMock);
    }

    @Test
    public void apply_StartFailsAndBlacklistIsDisabled_JarFileShouldNotBeBlacklisted() {
        given(launcherServiceMock.launchIntegrasjonspunkt(any())).willReturn(
                new LaunchResult()
                        .setStatus(LaunchStatus.FAILED)
                        .setStartupLog("theStartupLog")
        );

        given(applicationMock.getCurrent())
                .willReturn(new ApplicationMetadata().setVersion("latest"));
        given(applicationMock.isSameVersion()).willReturn(true);
        given(actuatorServiceMock.getStatus()).willReturn(HealthStatus.DOWN);
        given(fileMock.getAbsolutePath()).willReturn("the path");
        given(blocklistProperties.isEnabled()).willReturn(false);

        assertThat(target.apply(applicationMock)).isSameAs(applicationMock);

        verify(launcherServiceMock).launchIntegrasjonspunkt("the path");
        verify(mailService).sendMail("Upgrade FAILED test.jar", "theStartupLog");
        verify(kosmosDirectoryRepoMock, never()).blockList(fileMock);
    }

    @Test
    public void apply_whenStartFailsAndTheApplicationIsRunning_theJarFileShouldBeBlacklistedAndAShutdownTriggered() {
        given(launcherServiceMock.launchIntegrasjonspunkt(any())).willReturn(
                new LaunchResult()
                        .setStatus(LaunchStatus.FAILED)
                        .setStartupLog("theStartupLog")
        );

        given(applicationMock.getCurrent())
                .willReturn(new ApplicationMetadata().setVersion("latest"));
        given(applicationMock.isSameVersion()).willReturn(true);
        given(actuatorServiceMock.getStatus()).willReturn(HealthStatus.DOWN, HealthStatus.UP);
        given(fileMock.getAbsolutePath()).willReturn("the path");
        assertThat(target.apply(applicationMock)).isSameAs(applicationMock);
        verify(launcherServiceMock).launchIntegrasjonspunkt("the path");
        verify(mailService).sendMail("Upgrade FAILED test.jar", "theStartupLog");
        verify(kosmosDirectoryRepoMock).blockList(fileMock);
        verify(actuatorServiceMock).shutdown();
    }

    @Test
    public void apply_StartFailsAndTheApplicationIsRunningAndBlacklistIsDisabled_JarShouldNotBeBlacklistedAndAShutdownIsNotTriggered() {
        given(launcherServiceMock.launchIntegrasjonspunkt(any())).willReturn(
                new LaunchResult()
                        .setStatus(LaunchStatus.FAILED)
                        .setStartupLog("theStartupLog")
        );

        given(applicationMock.getCurrent())
                .willReturn(new ApplicationMetadata().setVersion("latest"));
        given(applicationMock.isSameVersion()).willReturn(true);
        given(actuatorServiceMock.getStatus()).willReturn(HealthStatus.DOWN, HealthStatus.UP);
        given(fileMock.getAbsolutePath()).willReturn("the path");
        given(blocklistProperties.isEnabled()).willReturn(false);

        assertThat(target.apply(applicationMock)).isSameAs(applicationMock);

        verify(launcherServiceMock).launchIntegrasjonspunkt("the path");
        verify(mailService).sendMail("Upgrade FAILED test.jar", "theStartupLog");
        verify(kosmosDirectoryRepoMock, never()).blockList(fileMock);
        verify(actuatorServiceMock, never()).shutdown();
    }

}
