package com.benggri.example.core.member;

import com.benggri.example.core.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberRepositoryTest extends PostgresTestBase {

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void findByEmail_returnsSavedMember() {
        memberRepository.save(Member.create("a@example.com", "hash", "Alice", Role.USER));

        var found = memberRepository.findByEmail("a@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Alice");
        assertThat(found.get().getRole()).isEqualTo(Role.USER);
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void existsByEmail_falseForUnknown() {
        assertThat(memberRepository.existsByEmail("nobody@example.com")).isFalse();
    }
}
