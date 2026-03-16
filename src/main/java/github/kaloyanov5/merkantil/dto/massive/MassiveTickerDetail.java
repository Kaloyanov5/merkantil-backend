package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * Represents a single ticker from /v3/reference/tickers or /v3/reference/tickers/{ticker}.
 * Used for asset discovery / stock import.
 */
@Data
public class MassiveTickerDetail {
    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("name")
    private String name;

    @JsonProperty("market")
    private String market;

    @JsonProperty("locale")
    private String locale;

    @JsonProperty("primary_exchange")
    private String primaryExchange; // MIC code e.g. XNAS, XNYS

    @JsonProperty("type")
    private String type; // CS = common stock

    @JsonProperty("active")
    private Boolean active;

    @JsonProperty("currency_name")
    private String currencyName;

    @JsonProperty("cik")
    private String cik;

    @JsonProperty("composite_figi")
    private String compositeFigi;

    @JsonProperty("share_class_figi")
    private String shareClassFigi;

    @JsonProperty("last_updated_utc")
    private String lastUpdatedUtc;

    @JsonProperty("delisted_utc")
    private String delistedUtc;

    // Fields available from /v3/reference/tickers/{ticker} (detail endpoint)
    @JsonProperty("market_cap")
    private Double marketCap;

    @JsonProperty("sic_description")
    private String sicDescription; // Sector-like field

    @JsonProperty("sic_code")
    private String sicCode;

    @JsonProperty("description")
    private String description;

    @JsonProperty("homepage_url")
    private String homepageUrl;

    @JsonProperty("phone_number")
    private String phoneNumber;

    @JsonProperty("total_employees")
    private Integer totalEmployees;

    @JsonProperty("list_date")
    private String listDate;

    @JsonProperty("round_lot")
    private Integer roundLot;

    @JsonProperty("share_class_shares_outstanding")
    private Long shareClassSharesOutstanding;

    @JsonProperty("weighted_shares_outstanding")
    private Long weightedSharesOutstanding;

    @JsonProperty("ticker_root")
    private String tickerRoot;

    @JsonProperty("ticker_suffix")
    private String tickerSuffix;

    @JsonProperty("address")
    private Map<String, String> address; // address1, city, state, postal_code

    @JsonProperty("branding")
    private Map<String, String> branding; // logo_url, icon_url
}


