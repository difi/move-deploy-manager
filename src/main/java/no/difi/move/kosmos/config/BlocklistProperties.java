package no.difi.move.kosmos.config;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Data
public class BlocklistProperties {

    private boolean enabled;

    @NotNull
    @Positive
    private Integer durationInHours;
}
