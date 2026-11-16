package com.emrecanay.flashsale.order;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically returns the stock of orders that were never paid. Safe to run on every instance. */
@Component
class ReservationSweeper {

    private static final Logger log = LoggerFactory.getLogger(ReservationSweeper.class);

    private final OrderLifecycle lifecycle;
    private final ReservationProperties reservation;
    private final Clock clock;

    ReservationSweeper(OrderLifecycle lifecycle, ReservationProperties reservation, Clock clock) {
        this.lifecycle = lifecycle;
        this.reservation = reservation;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${flashsale.reservation.sweep-interval}")
    void sweep() {
        int total = 0;
        int expired;
        // Each batch is its own short transaction, so a large backlog never turns into one long lock-holding one.
        do {
            expired = lifecycle.expireOverdue(clock.instant());
            total += expired;
        }
        while (expired == reservation.sweepBatchSize());

        if (total > 0) {
            log.info("Expired {} unpaid order(s) and released their stock", total);
        }
    }
}
