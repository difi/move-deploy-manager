package no.difi.move.deploymanager.config;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Data
public class IntegrasjonspunktProperties {

    @NotNull
    @Pattern(regexp = "dev|itest|staging|production")
    private String profile;

    @NotNull
    private String latestVersion;
    @NotNull
    private String latestChecksum;
}
