package pe.axiz.payment.adapter.in.rest.provider;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.axiz.payment.adapter.out.psp.dto.PspCapabilitiesResponse;
import pe.axiz.payment.cache.ProviderCapabilitiesCacheService;

@RestController
@RequestMapping("/api/v1/payments/provider-capabilities")
public class ProviderCapabilitiesController {

    private final ProviderCapabilitiesCacheService cacheService;

    public ProviderCapabilitiesController(ProviderCapabilitiesCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @GetMapping
    public PspCapabilitiesResponse capabilities(@RequestParam(defaultValue = "psp") String provider) {
        return cacheService.get(provider);
    }

    @GetMapping("/cache-metrics")
    public Map<String, Long> cacheMetrics() {
        return cacheService.metrics();
    }
}
