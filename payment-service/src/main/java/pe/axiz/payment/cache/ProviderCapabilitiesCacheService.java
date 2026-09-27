package pe.axiz.payment.cache;

import io.github.resilience4j.cache.Cache;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import pe.axiz.payment.adapter.out.psp.PspHttpClient;
import pe.axiz.payment.adapter.out.psp.dto.PspCapabilitiesResponse;

@Service
public class ProviderCapabilitiesCacheService {

    private final Cache<String, PspCapabilitiesResponse> cache;
    private final Function<String, PspCapabilitiesResponse> cachedLookup;

    public ProviderCapabilitiesCacheService(
            Cache<String, PspCapabilitiesResponse> cache,
            PspHttpClient pspHttpClient) {
        this.cache = cache;
        this.cachedLookup = Cache.decorateSupplier(cache, pspHttpClient::capabilities);
    }

    public PspCapabilitiesResponse get(String provider) {
        if (!"psp".equalsIgnoreCase(provider)) {
            throw new IllegalArgumentException("Proveedor no soportado: " + provider);
        }
        return cachedLookup.apply(provider.toLowerCase());
    }

    public Map<String, Long> metrics() {
        return Map.of(
                "hits", cache.getMetrics().getNumberOfCacheHits(),
                "misses", cache.getMetrics().getNumberOfCacheMisses());
    }
}
