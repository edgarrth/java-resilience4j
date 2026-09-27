package pe.axiz.payment.resilience;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

@Component
public class ResilienceEventJournal {

    private static final int MAX_EVENTS = 500;
    private final ConcurrentLinkedDeque<ResilienceEvent> events = new ConcurrentLinkedDeque<>();

    public void add(String component, String instance, Object event) {
        events.addFirst(new ResilienceEvent(
                Instant.now(),
                component,
                instance,
                event.getClass().getSimpleName(),
                event.toString()));
        while (events.size() > MAX_EVENTS) {
            events.pollLast();
        }
    }

    public List<ResilienceEvent> latest(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        List<ResilienceEvent> result = new ArrayList<>(boundedLimit);
        for (ResilienceEvent event : events) {
            if (result.size() == boundedLimit) {
                break;
            }
            result.add(event);
        }
        return List.copyOf(result);
    }

    public void clear() {
        events.clear();
    }
}
