package com.benggri.example.core.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.benggri.example.core.common.ForbiddenException;
import com.benggri.example.core.common.NotFoundException;
import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.member.repository.MemberRepository;
import com.benggri.example.core.post.dto.PostResult;
import com.benggri.example.core.post.dto.PostSearchCondition;
import com.benggri.example.core.post.entity.Post;
import com.benggri.example.core.post.repository.PostRepository;
import com.benggri.example.core.security.AuthUser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final PostSearchCondition COND = new PostSearchCondition(null, null);

    @Mock
    private PostRepository postRepository;

    @Mock
    private MemberRepository memberRepository;

    private PostService service() {
        return new PostService(postRepository, memberRepository);
    }

    private Member owner() {
        Member m = Member.create("o@x.com", "hash", "Owner", Role.USER);
        ReflectionTestUtils.setField(m, "id", 1L);
        return m;
    }

    private Post post() {
        Post p = Post.create(owner(), "t", "c");
        ReflectionTestUtils.setField(p, "id", 10L);
        return p;
    }

    private Pageable capturedSearchPageable(Pageable input) {
        when(postRepository.search(any(), any())).thenReturn(Page.empty());
        service().search(COND, input);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).search(any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void create_savesPostOwnedByActor() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(owner()));
        when(postRepository.save(any(Post.class))).thenAnswer(i -> i.getArgument(0));

        PostResult result = service().create(new AuthUser(1L, Role.USER), "t", "c");

        assertThat(result.memberId()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("t");
    }

    @Test
    void post_updateByOtherUserThrowsForbidden() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(post()));

        assertThatThrownBy(() -> service().update(new AuthUser(2L, Role.USER), 10L, "n", "n"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void post_updateByAdminSucceeds() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(post()));

        PostResult result = service().update(new AuthUser(2L, Role.ADMIN), 10L, "n", "m");

        assertThat(result.title()).isEqualTo("n");
        assertThat(result.content()).isEqualTo("m");
    }

    @Test
    void post_updateByOwnerSucceeds() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(post()));

        PostResult result = service().update(new AuthUser(1L, Role.USER), 10L, "n", "m");

        assertThat(result.title()).isEqualTo("n");
    }

    @Test
    void post_deleteMissingThrowsNotFound() {
        when(postRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(new AuthUser(2L, Role.USER), 10L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void post_deleteByOtherUserThrowsForbidden() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(post()));

        assertThatThrownBy(() -> service().delete(new AuthUser(2L, Role.USER), 10L))
                .isInstanceOf(ForbiddenException.class);
        verify(postRepository, never()).delete(any());
    }

    @Test
    void post_deleteByOwnerDeletes() {
        Post p = post();
        when(postRepository.findById(10L)).thenReturn(Optional.of(p));

        service().delete(new AuthUser(1L, Role.USER), 10L);

        verify(postRepository).delete(p);
    }

    @Test
    void get_mapsPost() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(post()));

        assertThat(service().get(10L).memberId()).isEqualTo(1L);
    }

    @Test
    void search_mapsResults() {
        when(postRepository.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(post()), PageRequest.of(0, 20), 1));

        Page<PostResult> page = service().search(COND, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(PostResult::id).containsExactly(10L);
    }

    @Test
    void search_sizeOver100ClampedTo100() {
        Pageable used = capturedSearchPageable(PageRequest.of(2, 500, Sort.by("title")));

        assertThat(used.getPageSize()).isEqualTo(100);
        assertThat(used.getPageNumber()).isEqualTo(2);
    }

    @Test
    void search_unpagedUsesDefaultPageOf20() {
        Pageable used = capturedSearchPageable(Pageable.unpaged());

        assertThat(used.isPaged()).isTrue();
        assertThat(used.getPageNumber()).isZero();
        assertThat(used.getPageSize()).isEqualTo(20);
    }

    @Test
    void search_sizeWithinLimitKept() {
        Pageable used = capturedSearchPageable(PageRequest.of(1, 50));

        assertThat(used.getPageSize()).isEqualTo(50);
        assertThat(used.getPageNumber()).isEqualTo(1);
    }
}
