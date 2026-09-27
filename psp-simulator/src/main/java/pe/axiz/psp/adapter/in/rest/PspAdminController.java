package pe.axiz.psp.adapter.in.rest;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.axiz.psp.application.PspSimulatorService;
import pe.axiz.psp.domain.Authorization;
import pe.axiz.psp.domain.Scenario;

@RestController
@RequestMapping("/admin")
public class PspAdminController {

    private final PspSimulatorService service;

    public PspAdminController(PspSimulatorService service) {
        this.service = service;
    }

    @PostMapping("/scenarios/import")
    public List<Scenario> importScenarios(@RequestBody Collection<Scenario> scenarios) {
        service.importScenarios(scenarios);
        return service.scenarios();
    }

    @PostMapping("/scenarios/{name}/activate")
    public Scenario activate(@PathVariable String name) {
        return service.activate(name);
    }

    @GetMapping("/scenarios")
    public Map<String, Object> scenarios() {
        return Map.of(
                "active", service.activeScenario().name(),
                "scenarios", service.scenarios());
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return service.stats();
    }

    @GetMapping("/authorizations")
    public List<Authorization> authorizations() {
        return service.authorizations();
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        service.reset();
        return ResponseEntity.noContent().build();
    }
}
