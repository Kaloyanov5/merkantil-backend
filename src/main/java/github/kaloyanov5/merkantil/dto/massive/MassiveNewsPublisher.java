package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MassiveNewsPublisher {

    @JsonProperty("name")
    private String name;

    @JsonProperty("homepage_url")
    private String homepageUrl;

    @JsonProperty("logo_url")
    private String logoUrl;

    @JsonProperty("favicon_url")
    private String faviconUrl;
}
