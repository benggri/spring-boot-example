package com.benggri.example.core.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.benggri.example.core.common.DuplicateException;
import com.benggri.example.core.common.ForbiddenException;
import com.benggri.example.core.common.NotFoundException;
import com.benggri.example.core.post.PostRepository;
import com.benggri.example.core.security.AuthUser;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PostRepository postRepository;

    private MemberService service() {
        return new MemberService(memberRepository, postRepository, encoder);
    }

    private Member member(Long id, Role role) {
        Member m = Member.create("a@x.com", "hash", "A", role);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    @Test
    void register_duplicateEmailThrowsDuplicate() {
        when(memberRepository.existsByEmail("a@x.com")).thenReturn(true);

        assertThatThrownBy(() -> service().register("a@x.com", "pw", "A"))
                .isInstanceOf(DuplicateException.class);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_storesHashedPassword() {
        when(memberRepository.existsByEmail("a@x.com")).thenReturn(false);
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(i -> i.getArgument(0));

        MemberResult result = service().register("a@x.com", "pw", "A");

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveAndFlush(captor.capture());
        String hash = captor.getValue().getPasswordHash();
        assertThat(hash).isNotEqualTo("pw");
        assertThat(encoder.matches("pw", hash)).isTrue();
        assertThat(result.email()).isEqualTo("a@x.com");
        assertThat(result.role()).isEqualTo(Role.USER);
    }

    @Test
    void memberResult_hasNoPasswordHashComponent() {
        assertThat(Arrays.stream(MemberResult.class.getRecordComponents())
                .map(RecordComponent::getName))
                .doesNotContain("passwordHash");
    }

    @Test
    void update_byOtherUserThrowsForbidden() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1L, Role.USER)));

        assertThatThrownBy(() -> service().update(new AuthUser(2L, Role.USER), 1L, "B"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void update_bySelfRenames() {
        Member m = member(1L, Role.USER);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(m));

        MemberResult result = service().update(new AuthUser(1L, Role.USER), 1L, "B");

        assertThat(result.name()).isEqualTo("B");
    }

    @Test
    void delete_byAdminSucceeds() {
        Member m = member(1L, Role.USER);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(m));

        service().delete(new AuthUser(9L, Role.ADMIN), 1L);

        InOrder order = inOrder(postRepository, memberRepository);
        order.verify(postRepository).deleteByMemberId(1L);
        order.verify(memberRepository).delete(m);
    }

    @Test
    void delete_byOtherUserDoesNotTouchPosts() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1L, Role.USER)));

        assertThatThrownBy(() -> service().delete(new AuthUser(2L, Role.USER), 1L))
                .isInstanceOf(ForbiddenException.class);
        verify(postRepository, never()).deleteByMemberId(any());
    }

    @Test
    void register_concurrentDuplicateMapsToDuplicateException() {
        when(memberRepository.existsByEmail("a@x.com")).thenReturn(false);
        when(memberRepository.saveAndFlush(any(Member.class)))
                .thenThrow(new DataIntegrityViolationException("uk_members_email"));

        assertThatThrownBy(() -> service().register("a@x.com", "pw", "A"))
                .isInstanceOf(DuplicateException.class)
                .hasMessageNotContaining("pw");
    }

    @Test
    void delete_missingThrowsNotFoundEvenForOthers() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(new AuthUser(2L, Role.USER), 1L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void get_missingThrowsNotFound() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(1L)).isInstanceOf(NotFoundException.class);
    }
}
