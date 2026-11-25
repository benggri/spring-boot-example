package com.benggri.example.core.member;

import com.benggri.example.core.common.DuplicateException;
import com.benggri.example.core.common.ForbiddenException;
import com.benggri.example.core.common.NotFoundException;
import com.benggri.example.core.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public MemberResult register(String email, String rawPassword, String name) {
        if (memberRepository.existsByEmail(email)) {
            throw new DuplicateException("Email already registered: " + email);
        }
        Member member = Member.create(email, passwordEncoder.encode(rawPassword), name, Role.USER);
        return MemberResult.from(memberRepository.save(member));
    }

    @Transactional(readOnly = true)
    public MemberResult get(Long id) {
        return MemberResult.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<MemberResult> list(Pageable pageable) {
        return memberRepository.findAll(pageable).map(MemberResult::from);
    }

    public MemberResult update(AuthUser actor, Long id, String name) {
        Member member = find(id);
        requireSelfOrAdmin(actor, id);
        member.rename(name);
        return MemberResult.from(member);
    }

    public void delete(AuthUser actor, Long id) {
        Member member = find(id);
        requireSelfOrAdmin(actor, id);
        memberRepository.delete(member);
    }

    private Member find(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Member not found: " + id));
    }

    private void requireSelfOrAdmin(AuthUser actor, Long targetId) {
        if (!actor.isAdmin() && !targetId.equals(actor.id())) {
            throw new ForbiddenException("Not allowed to modify member " + targetId);
        }
    }
}
