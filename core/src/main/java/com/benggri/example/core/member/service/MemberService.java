package com.benggri.example.core.member.service;

import com.benggri.example.core.aop.Audited;
import com.benggri.example.core.common.DuplicateException;
import com.benggri.example.core.common.ForbiddenException;
import com.benggri.example.core.common.InvalidRequestException;
import com.benggri.example.core.common.NotFoundException;
import com.benggri.example.core.common.TextGuard;
import com.benggri.example.core.member.dto.AuthorResult;
import com.benggri.example.core.member.dto.MemberResult;
import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.member.repository.MemberRepository;
import com.benggri.example.core.post.repository.PostRepository;
import com.benggri.example.core.security.AuthUser;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemberService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PostRepository postRepository,
            PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.postRepository = postRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Audited("member.register")
    public MemberResult register(String email, String rawPassword, String name) {
        TextGuard.requireClean(email);
        TextGuard.requireClean(name);
        requireHashablePassword(rawPassword);
        if (memberRepository.existsByEmail(email)) {
            throw new DuplicateException("Email already registered: " + email);
        }
        Member member = Member.create(email, passwordEncoder.encode(rawPassword), name, Role.USER);
        try {
            return MemberResult.from(memberRepository.saveAndFlush(member));
        } catch (DataIntegrityViolationException e) {
            // the unique index on email is the authority when two registrations race
            throw new DuplicateException("Email already registered: " + email);
        }
    }

    /** BCrypt throws on more than 72 bytes; reject first so callers get a clean error, not a 500. */
    private static void requireHashablePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidRequestException("Invalid input");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new InvalidRequestException("Password is too long");
        }
    }

    @Transactional(readOnly = true)
    public MemberResult get(AuthUser actor, Long id) {
        Member member = find(id);
        requireSelfOrAdmin(actor, id);
        return MemberResult.from(member);
    }

    @Transactional(readOnly = true)
    public Page<MemberResult> list(AuthUser actor, Pageable pageable) {
        if (!actor.isAdmin()) {
            throw new ForbiddenException("Only an admin may list members");
        }
        return memberRepository.findAll(pageable).map(MemberResult::from);
    }

    /**
     * Batch lookup of public author data (id and name only) in one query. No authorization check:
     * names are visible to any authenticated caller. Unknown ids are simply absent.
     */
    @Transactional(readOnly = true)
    public Map<Long, AuthorResult> findAuthors(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findAllById(ids).stream()
                .map(AuthorResult::from)
                .collect(Collectors.toMap(AuthorResult::id, Function.identity()));
    }

    @Audited("member.update")
    public MemberResult update(AuthUser actor, Long id, String name) {
        TextGuard.requireClean(name);
        Member member = find(id);
        requireSelfOrAdmin(actor, id);
        member.rename(name);
        return MemberResult.from(member);
    }

    @Audited("member.delete")
    public void delete(AuthUser actor, Long id) {
        Member member = find(id);
        requireSelfOrAdmin(actor, id);
        postRepository.deleteByMemberId(id);
        memberRepository.delete(member);
    }

    private Member find(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Member not found: " + id));
    }

    private void requireSelfOrAdmin(AuthUser actor, Long targetId) {
        if (!actor.isAdmin() && !targetId.equals(actor.id())) {
            throw new ForbiddenException("Not allowed to access member " + targetId);
        }
    }
}
