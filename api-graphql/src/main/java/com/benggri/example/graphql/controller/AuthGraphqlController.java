package com.benggri.example.graphql.controller;

import com.benggri.example.core.member.dto.MemberResult;
import com.benggri.example.core.member.service.MemberService;
import com.benggri.example.core.security.AuthService;
import com.benggri.example.core.security.TokenPair;
import com.benggri.example.graphql.dto.LoginInput;
import com.benggri.example.graphql.dto.RegisterInput;
import jakarta.validation.Valid;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

/**
 * The only operations reachable without a token (so they carry no {@code @PreAuthorize}); the
 * guard test pins this set to exactly register, login and refresh.
 */
@Controller
public class AuthGraphqlController {

    private final MemberService memberService;
    private final AuthService authService;

    public AuthGraphqlController(MemberService memberService, AuthService authService) {
        this.memberService = memberService;
        this.authService = authService;
    }

    @MutationMapping
    public MemberResult register(@Argument @Valid RegisterInput input) {
        return memberService.register(input.email(), input.password(), input.name());
    }

    @MutationMapping
    public TokenPair login(@Argument @Valid LoginInput input) {
        return authService.login(input.email(), input.password());
    }

    @MutationMapping
    public TokenPair refresh(@Argument String refreshToken) {
        return authService.refresh(refreshToken);
    }
}
