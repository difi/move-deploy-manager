package no.difi.move.deploymanager.cucumber;

import com.github.tomakehurst.wiremock.WireMockServer;
import lombok.RequiredArgsConstructor;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@RequiredArgsConstructor
public class WireMockMonitor {

    private final WireMockServer server;

    @PostConstruct
    public void start() {
        server.start();
    }

    @PreDestroy
    public void stop() {
        server.shutdown();
    }
}
