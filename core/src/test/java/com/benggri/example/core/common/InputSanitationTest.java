package com.benggri.example.core.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.member.repository.MemberRepository;
import com.benggri.example.core.member.service.MemberService;
import com.benggri.example.core.post.dto.PostSearchCondition;
import com.benggri.example.core.post.repository.PostRepository;
import com.benggri.example.core.post.service.PostService;
import com.benggri.example.core.security.AuthService;
import com.benggri.example.core.security.AuthUser;
import com.benggri.example.core.security.JwtProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** NUL and other control characters must be rejected before any repository (SQL) call. */
class InputSanitationTest {

    private static final String NUL = "a\u0000b";
    private static final AuthUser USER = new AuthUser(1L, Role.USER);

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final MemberService members = new MemberService(memberRepository, postRepository, encoder);
    private final PostService posts = new PostService(postRepository, memberRepository);
    private final AuthService auth = new AuthService(memberRepository, encoder, mock(JwtProvider.class));

    @Test
    void register_emailWithNulThrowsInvalidRequestBeforeAnyRepositoryCall() {
        assertThatThrownBy(() -> members.register("a\u0000@example.com", "password-ok-1", "Name"))
                .isInstanceOf(InvalidRequestException.class).hasMessage("Invalid input");
        verifyNoInteractions(memberRepository, postRepository);
    }

    @Test
    void register_nameWithNulThrowsInvalidRequest() {
        assertThatThrownBy(() -> members.register("a@example.com", "password-ok-1", NUL))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(memberRepository);
    }

    @Test
    void register_blankPasswordThrowsInvalidRequest() {
        for (String blank : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> members.register("a@example.com", blank, "Name"))
                    .isInstanceOf(InvalidRequestException.class).hasMessage("Invalid input");
        }
        verifyNoInteractions(memberRepository);
    }

    @Test
    void update_nameWithNulThrowsInvalidRequest() {
        assertThatThrownBy(() -> members.update(USER, 1L, NUL)).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(memberRepository);
    }

    @Test
    void login_emailWithNulThrowsInvalidRequestAndNeverQueriesRepository() {
        assertThatThrownBy(() -> auth.login(NUL, "whatever"))
                .isInstanceOf(InvalidRequestException.class).hasMessage("Invalid input");
        verifyNoInteractions(memberRepository);
    }

    @Test
    void createPost_titleOrContentWithNulThrowsInvalidRequest() {
        assertThatThrownBy(() -> posts.create(USER, NUL, "content")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> posts.create(USER, "title", NUL)).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(memberRepository, postRepository);
    }

    @Test
    void updatePost_titleOrContentWithNulThrowsInvalidRequest() {
        assertThatThrownBy(() -> posts.update(USER, 1L, NUL, "content")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> posts.update(USER, 1L, "title", NUL)).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(postRepository);
    }

    @Test
    void search_keywordWithNulThrowsInvalidRequest() {
        assertThatThrownBy(() -> posts.search(new PostSearchCondition(NUL, null), PageRequest.of(0, 10)))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(postRepository);
    }

    @Test
    void search_keywordLongerThan200ThrowsInvalidRequest() {
        assertThatThrownBy(() -> posts.search(new PostSearchCondition("k".repeat(201), null), PageRequest.of(0, 10)))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(postRepository);
    }

    @Test
    void search_keywordOf200CharsIsAccepted() {
        when(postRepository.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        assertThatCode(() -> posts.search(new PostSearchCondition("k".repeat(200), null), PageRequest.of(0, 10)))
                .doesNotThrowAnyException();
    }

    @Test
    void register_cleanInputStillRegisters() {
        when(memberRepository.existsByEmail(any())).thenReturn(false);
        when(memberRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        assertThatCode(() -> members.register("a@example.com", "password-ok-1", "Name\twith\ttabs"))
                .doesNotThrowAnyException();
    }
}
