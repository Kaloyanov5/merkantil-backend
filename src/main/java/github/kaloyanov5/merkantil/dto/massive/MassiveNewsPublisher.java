package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MassiveNewsPublisher(
        @JsonProperty("name") String name,
        @JsonProperty("homepage_url") String homepageUrl,
        @JsonProperty("logo_url") String logoUrl,
        @JsonProperty("favicon_url") String faviconUrl
) {
}
