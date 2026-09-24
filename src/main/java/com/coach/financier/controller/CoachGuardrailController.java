package com.coach.financier.controller;

import com.coach.financier.service.CoachGuardrailService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint de supervision des garde-fous conversationnels, sans exposer les messages analysés. */
@RestController
@RequestMapping("/api/guardrail")
public class CoachGuardrailController {
    private final CoachGuardrailService guardrailService;

    public CoachGuardrailController(CoachGuardrailService guardrailService) {
        this.guardrailService = guardrailService;
    }

    @GetMapping("/sessions/{sessionId}")
    public CoachGuardrailService.SessionStatus sessionStatus(@PathVariable String sessionId) {
        return guardrailService.sessionStatus(sessionId);
    }
}

