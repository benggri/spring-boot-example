package com.benggri.example.core.member.dto;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;

public record MemberResult(Long id, String email, String name, Role role) {

    public static MemberResult from(Member member) {
        return new MemberResult(member.getId(), member.getEmail(), member.getName(), member.getRole());
    }
}
