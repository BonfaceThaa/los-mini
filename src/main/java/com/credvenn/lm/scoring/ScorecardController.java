package com.credvenn.lm.scoring;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/origination-profiles/{profileCode}/scorecards")
@PreAuthorize("hasAuthority('APPLICATION_VARIABLE_MANAGE')")
@RequiredArgsConstructor
public class ScorecardController {
    private final ScorecardService service;
    @PostMapping public ScorecardDtos.Response create(@PathVariable String profileCode, @Valid @RequestBody ScorecardDtos.Draft request) { return service.create(profileCode, request); }
    @PutMapping("/{id}") public ScorecardDtos.Response update(@PathVariable String profileCode, @PathVariable String id, @Valid @RequestBody ScorecardDtos.Draft request) { return service.update(profileCode, id, request); }
    @GetMapping public List<ScorecardDtos.Response> list(@PathVariable String profileCode) { return service.list(profileCode); }
    @GetMapping("/{id}") public ScorecardDtos.Response get(@PathVariable String profileCode, @PathVariable String id) { return service.get(profileCode, id); }
    @PostMapping("/{id}/validate") public ResponseEntity<Void> validate(@PathVariable String profileCode, @PathVariable String id) { service.validate(profileCode, id); return ResponseEntity.noContent().build(); }
    @PostMapping("/{id}/publish") public ScorecardDtos.Response publish(@PathVariable String profileCode, @PathVariable String id) { return service.publish(profileCode, id); }
}
