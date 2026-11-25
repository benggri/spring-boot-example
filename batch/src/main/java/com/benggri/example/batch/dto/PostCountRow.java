package com.benggri.example.batch.dto;

/** Number of posts one member wrote on the run date, as read by the aggregation step. */
public record PostCountRow(Long memberId, long postCount) {
}
