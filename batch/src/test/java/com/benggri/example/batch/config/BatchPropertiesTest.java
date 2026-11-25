package com.benggri.example.batch.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class BatchPropertiesTest {

    @Test
    void batchProperties_rejectInvalidValues() {
        assertThatThrownBy(() -> new BatchProperties(0, 90)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchProperties(100, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchProperties(-1, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void batchProperties_defaultsApplyWhenUnset() {
        BatchProperties props = new Binder(new MapConfigurationPropertySource())
                .bindOrCreate("app.batch", BatchProperties.class);
        assertThat(props.chunkSize()).isEqualTo(100);
        assertThat(props.retentionDays()).isEqualTo(90);
    }
}
