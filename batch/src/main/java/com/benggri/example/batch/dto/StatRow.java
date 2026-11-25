package com.benggri.example.batch.dto;

import java.time.LocalDate;

/** One row of member_post_stats, as written by the aggregation step. */
public record StatRow(Long memberId, LocalDate statDate, int postCount) {
}
