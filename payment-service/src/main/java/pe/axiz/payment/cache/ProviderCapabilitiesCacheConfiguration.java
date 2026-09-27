package pe.axiz.payment.cache;

import io.github.resilience4j.cache.Cache;
import java.util.concurrent.TimeUnit;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.axiz.payment.adapter.out.psp.dto.PspCapabilitiesResponse;

@Configuration
public class ProviderCapabilitiesCacheConfiguration {

    @Bean(destroyMethod = "close")
    CacheManager providerCapabilitiesCacheManager() {
        return Caching.getCachingProvider().getCacheManager();
    }

    @Bean
    Cache<String, PspCapabilitiesResponse> providerCapabilitiesCache(CacheManager cacheManager) {
        MutableConfiguration<String, PspCapabilitiesResponse> configuration =
                new MutableConfiguration<String, PspCapabilitiesResponse>()
                        .setTypes(String.class, PspCapabilitiesResponse.class)
                        .setStoreByValue(false)
                        .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
                                new Duration(TimeUnit.SECONDS, 30)));

        javax.cache.Cache<String, PspCapabilitiesResponse> jCache =
                cacheManager.getCache("psp-capabilities", String.class, PspCapabilitiesResponse.class);
        if (jCache == null) {
            jCache = cacheManager.createCache("psp-capabilities", configuration);
        }
        return Cache.of(jCache);
    }
}
