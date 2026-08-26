package com.seewik.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class ApiController {
    private final CivicRouterService civicRouterService;
    private final PrabhagResolverService prabhagResolverService;

    public ApiController(
            CivicRouterService civicRouterService,
            PrabhagResolverService prabhagResolverService) {
        this.civicRouterService = civicRouterService;
        this.prabhagResolverService = prabhagResolverService;
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
        return civicRouterService.route(request);
    }

    @GetMapping(value = {"/healthz", "/health"}, produces = "application/json")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "seewik-api");
    }

}
