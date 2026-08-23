package com.seewik.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping
public class ApiController {
    private final GeminiService geminiService;
    private final CivicRouterService civicRouterService;

    public ApiController(GeminiService geminiService, CivicRouterService civicRouterService) {
        this.geminiService = geminiService;
        this.civicRouterService = civicRouterService;
    }

    @PostMapping(value = "/api/civic/route", consumes = "application/json", produces = "application/json")
    public CivicRouterService.CivicRouteResponse civicRoute(
            @org.springframework.web.bind.annotation.RequestBody CivicRouterService.CivicRouteRequest request) {
        return civicRouterService.route(request);
    }

    @GetMapping({"/healthz", "/health"})
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "seewik-api");
    }

    @PostMapping("/api/gemini/smoke")
    public Map<String, String> geminiSmoke(@RequestParam(defaultValue = "Briefly describe what civic issue reporting means.") String prompt) throws Exception {
        return Map.of("model", "gemini-3.7-flash", "location", "global", "text", geminiService.generate(prompt, null, null));
    }

    @PostMapping(value = "/api/gemini/image-smoke", consumes = "multipart/form-data")
    public Map<String, String> geminiImageSmoke(
            @RequestParam("image") MultipartFile image,
            @RequestParam(defaultValue = "Briefly describe this harmless test image.") String prompt) throws Exception {
        if (image.isEmpty() || image.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Image must be between 1 byte and 5 MB");
        }
        return Map.of(
                "model", "gemini-3.7-flash",
                "location", "global",
                "text", geminiService.generate(prompt, image.getBytes(), image.getContentType()));
    }
}
