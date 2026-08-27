package com.seewik.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping
public class ApiController {
    private final CivicRouterService civicRouterService;
    private final PrabhagResolverService prabhagResolverService;
    private final OperationalMetrics metrics;

    @Autowired
    public ApiController(
            CivicRouterService civicRouterService,
            PrabhagResolverService prabhagResolverService,
            OperationalMetrics metrics) {
        this.civicRouterService = civicRouterService;
        this.prabhagResolverService = prabhagResolverService;
        this.metrics = metrics;
    }

    ApiController(CivicRouterService civicRouterService, PrabhagResolverService prabhagResolverService) {
        this(civicRouterService, prabhagResolverService,
                new OperationalMetrics(new com.fasterxml.jackson.databind.ObjectMapper(), "test"));
    }

    @PostMapping(value = "/api/civic/resolve-prabhag", consumes = "application/json", produces = "application/json")
    public PrabhagResolverService.PrabhagResolution resolvePrabhag(
            @org.springframework.web.bind.annotation.RequestBody
                    PrabhagResolverService.PrabhagResolutionRequest request) {
        return prabhagResolverService.resolve(request);
    }

    @PostMapping(value = "/api/civic/route", consumes = "application/json", produces = "application/json")
    public CivicRouterService.CivicRouteResponse civicRoute(
            @org.springframework.web.bind.annotation.RequestBody CivicRouterService.CivicRouteRequest request) {
        CivicRouterService.CivicRouteResponse response = civicRouterService.route(request);
        if ("SUPPORTED_ROUTE".equals(response.status())
                && "SELF_REPORTED".equals(response.resolutionMethod())) {
            metrics.increment("prabhag.manual_resolution");
        }
        return response;
    }

    @GetMapping(value = {"/healthz", "/health"}, produces = "application/json")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "seewik-api");
    }

}
