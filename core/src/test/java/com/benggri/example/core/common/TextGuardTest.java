package com.benggri.example.core.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TextGuardTest {

    @Test
    void nulAndOtherC0ControlsAreRejected() {
        for (char c = 0; c < 0x20; c++) {
            if (c == '\t' || c == '\n' || c == '\r') {
                continue;
            }
            String value = "ab" + c + "cd";
            assertThatThrownBy(() -> TextGuard.requireClean(value))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("Invalid input");
        }
    }

    @Test
    void tabNewlineCarriageReturnNullAndUnicodeAreAllowed() {
        assertThatCode(() -> TextGuard.requireClean("a\tb\nc\r\nd")).doesNotThrowAnyException();
        assertThatCode(() -> TextGuard.requireClean(null)).doesNotThrowAnyException();
        assertThatCode(() -> TextGuard.requireClean("한글 é 😀 😀")).doesNotThrowAnyException();
    }

    @Test
    void maxLengthCountsCharactersAndIgnoresNull() {
        assertThatCode(() -> TextGuard.requireMaxLength("a".repeat(5), 5)).doesNotThrowAnyException();
        assertThatCode(() -> TextGuard.requireMaxLength(null, 5)).doesNotThrowAnyException();
        assertThatThrownBy(() -> TextGuard.requireMaxLength("a".repeat(6), 5))
                .isInstanceOf(InvalidRequestException.class).hasMessage("Invalid input");
    }
}
