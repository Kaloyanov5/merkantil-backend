package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NewsPublisherResponse {
    private String name;
    private String homepageUrl;
    private String logoUrl;
    private String faviconUrl;
}
