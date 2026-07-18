package com.lardermind.scheduler;

import com.lardermind.service.MealPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MealPlanStatusScheduler {

    private final MealPlanService mealPlanService;

    /** Daily: PLANNED past serving_date → PENDING_CONFIRM; pending older than 7 days → SKIPPED. */
    @Scheduled(cron = "0 5 0 * * *")
    public void transitionMealPlanStatuses() {
        try {
            mealPlanService.transitionAllStatuses();
        } catch (Exception e) {
            log.error("Failed to transition meal plan statuses", e);
        }
    }
}
