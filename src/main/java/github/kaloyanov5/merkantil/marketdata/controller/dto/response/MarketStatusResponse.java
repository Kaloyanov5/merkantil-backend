package github.kaloyanov5.merkantil.marketdata.controller.dto.response;

import github.kaloyanov5.merkantil.common.constants.MarketSession;

public record MarketStatusResponse(MarketSession status, String serverTime) {}
