package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Represents a single ticker from /v3/reference/tickers or /v3/reference/tickers/{ticker}.
 * Used for asset discovery / stock import.
 */
public record MassiveTickerDetail(
        @JsonProperty("ticker") String ticker,
        @JsonProperty("name") String name,
        @JsonProperty("market") String market,
        @JsonProperty("locale") String locale,
        @JsonProperty("primary_exchange") String primaryExchange, // MIC code e.g. XNAS, XNYS
        @JsonProperty("type") String type, // CS = common stock
        @JsonProperty("active") Boolean active,
        @JsonProperty("currency_name") String currencyName,
        @JsonProperty("cik") String cik,
        @JsonProperty("composite_figi") String compositeFigi,
        @JsonProperty("share_class_figi") String shareClassFigi,
        @JsonProperty("last_updated_utc") String lastUpdatedUtc,
        @JsonProperty("delisted_utc") String delistedUtc,

        // Fields available from /v3/reference/tickers/{ticker} (detail endpoint)
        @JsonProperty("market_cap") Double marketCap,
        @JsonProperty("sic_description") String sicDescription, // Sector-like field
        @JsonProperty("sic_code") String sicCode,
        @JsonProperty("description") String description,
        @JsonProperty("homepage_url") String homepageUrl,
        @JsonProperty("phone_number") String phoneNumber,
        @JsonProperty("total_employees") Integer totalEmployees,
        @JsonProperty("list_date") String listDate,
        @JsonProperty("round_lot") Integer roundLot,
        @JsonProperty("share_class_shares_outstanding") Long shareClassSharesOutstanding,
        @JsonProperty("weighted_shares_outstanding") Long weightedSharesOutstanding,
        @JsonProperty("ticker_root") String tickerRoot,
        @JsonProperty("ticker_suffix") String tickerSuffix,
        @JsonProperty("address") Map<String, String> address, // address1, city, state, postal_code
        @JsonProperty("branding") Map<String, String> branding // logo_url, icon_url
) {
}
