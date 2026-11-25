package com.benggri.example.core.member;

public record MemberResult(Long id, String email, String name, Role role) {

    static MemberResult from(Member member) {
        return new MemberResult(member.getId(), member.getEmail(), member.getName(), member.getRole());
    }
}
