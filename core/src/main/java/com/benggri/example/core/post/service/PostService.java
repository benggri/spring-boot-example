package com.benggri.example.core.post.service;

import com.benggri.example.core.aop.Audited;
import com.benggri.example.core.common.ForbiddenException;
import com.benggri.example.core.common.NotFoundException;
import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.repository.MemberRepository;
import com.benggri.example.core.post.dto.PostResult;
import com.benggri.example.core.post.dto.PostSearchCondition;
import com.benggri.example.core.post.entity.Post;
import com.benggri.example.core.post.repository.PostRepository;
import com.benggri.example.core.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PostService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

    public PostService(PostRepository postRepository, MemberRepository memberRepository) {
        this.postRepository = postRepository;
        this.memberRepository = memberRepository;
    }

    @Audited("post.create")
    public PostResult create(AuthUser actor, String title, String content) {
        Member author = memberRepository.findById(actor.id())
                .orElseThrow(() -> new NotFoundException("Member not found: " + actor.id()));
        return PostResult.from(postRepository.save(Post.create(author, title, content)));
    }

    @Transactional(readOnly = true)
    public PostResult get(Long id) {
        return PostResult.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<PostResult> search(PostSearchCondition cond, Pageable pageable) {
        return postRepository.search(cond, normalize(pageable)).map(PostResult::from);
    }

    @Audited("post.update")
    public PostResult update(AuthUser actor, Long postId, String title, String content) {
        Post post = find(postId);
        requireOwnerOrAdmin(actor, post.getMember().getId());
        post.edit(title, content);
        return PostResult.from(post);
    }

    @Audited("post.delete")
    public void delete(AuthUser actor, Long postId) {
        Post post = find(postId);
        requireOwnerOrAdmin(actor, post.getMember().getId());
        postRepository.delete(post);
    }

    private Post find(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Post not found: " + id));
    }

    private Pageable normalize(Pageable pageable) {
        if (pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }
        // the repository fixes the ordering itself, so the caller's sort is intentionally dropped
        return PageRequest.of(pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
    }

    private void requireOwnerOrAdmin(AuthUser actor, Long ownerId) {
        if (!actor.isAdmin() && !ownerId.equals(actor.id())) {
            throw new ForbiddenException("Not allowed to modify this post");
        }
    }
}
