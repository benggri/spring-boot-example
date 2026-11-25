package com.benggri.example.graphql.controller;

import com.benggri.example.core.member.dto.MemberResult;
import com.benggri.example.core.member.service.MemberService;
import com.benggri.example.core.security.CurrentUser;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
public class MemberGraphqlController {

    private final MemberService memberService;

    public MemberGraphqlController(MemberService memberService) {
        this.memberService = memberService;
    }

    /** Self or ADMIN only (enforced by the service); there is deliberately no member list query. */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public MemberResult member(@Argument Long id) {
        return memberService.get(CurrentUser.get(), IdArguments.requireId(id));
    }
}
