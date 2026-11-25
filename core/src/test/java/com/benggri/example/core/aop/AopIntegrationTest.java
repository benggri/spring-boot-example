package com.benggri.example.core.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.benggri.example.core.common.NotFoundException;
import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.member.repository.MemberRepository;
import com.benggri.example.core.post.repository.PostRepository;
import com.benggri.example.core.post.service.PostService;
import com.benggri.example.core.security.AuthUser;
import com.benggri.example.core.support.PostgresTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AopIntegrationTest extends PostgresTestBase {

    @Autowired
    private PostService postService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    @AfterEach
    void cleanUp() {
        // committed rows would leak into other tests sharing the singleton container
        postRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void aop_appliesToTransactionalServiceProxies() {
        Member member = memberRepository.save(Member.create("aop@x.com", "h", "A", Role.USER));
        AuthUser actor = new AuthUser(member.getId(), Role.USER);
        try (var logs = new LogCapture("audit", Level.INFO)) {
            postService.create(actor, "title", "content");

            assertThat(logs.messages()).containsExactly(
                    "action=post.create actor=" + member.getId() + " outcome=SUCCESS");
        }
    }

    @Test
    void aop_auditsFailureAndPropagatesOriginalException() {
        AuthUser actor = new AuthUser(1L, Role.USER);
        try (var logs = new LogCapture("audit", Level.INFO)) {
            assertThatThrownBy(() -> postService.update(actor, -1L, "t", "c"))
                    .isInstanceOf(NotFoundException.class);

            assertThat(logs.messages()).containsExactly(
                    "action=post.update actor=1 outcome=FAILURE(NotFoundException)");
        }
    }
}
