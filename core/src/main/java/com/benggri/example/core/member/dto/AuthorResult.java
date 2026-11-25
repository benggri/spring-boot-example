package com.benggri.example.core.member.dto;

import com.benggri.example.core.member.entity.Member;

/** Public author view: deliberately has no email or role, which only the owner or an admin may read. */
public record AuthorResult(Long id, String name) {

    public static AuthorResult from(Member member) {
        return new AuthorResult(member.getId(), member.getName());
    }
}
