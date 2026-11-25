package com.benggri.example.batch.step;

import com.benggri.example.batch.dto.PostCountRow;
import com.benggri.example.batch.dto.StatRow;
import java.time.LocalDate;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/** Maps a counted row to a stat row for the run date; a zero count is filtered out (null). */
public class PostCountProcessor implements ItemProcessor<PostCountRow, StatRow> {

    private final LocalDate runDate;

    public PostCountProcessor(LocalDate runDate) {
        this.runDate = runDate;
    }

    @Override
    public StatRow process(PostCountRow item) {
        if (item.postCount() == 0) {
            return null;
        }
        return new StatRow(item.memberId(), runDate, Math.toIntExact(item.postCount()));
    }
}
