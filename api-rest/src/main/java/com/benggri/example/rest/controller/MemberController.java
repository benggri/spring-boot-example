package com.benggri.example.rest.controller;

import com.benggri.example.core.member.dto.MemberResult;
import com.benggri.example.core.member.service.MemberService;
import com.benggri.example.core.security.CurrentUser;
import com.benggri.example.rest.dto.RegisterRequest;
import com.benggri.example.rest.dto.UpdateMemberRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/members")
@Tag(name = "Members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping
    @SecurityRequirements
    @Operation(summary = "Register a member (no authentication required)")
    public ResponseEntity<MemberResult> register(@Valid @RequestBody RegisterRequest request) {
        MemberResult created = memberService.register(request.email(), request.password(), request.name());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a member (owner or admin)")
    public MemberResult get(@PathVariable Long id) {
        return memberService.get(CurrentUser.get(), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename a member (owner or admin)")
    public MemberResult update(@PathVariable Long id, @Valid @RequestBody UpdateMemberRequest request) {
        return memberService.update(CurrentUser.get(), id, request.name());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a member and their posts (owner or admin)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        memberService.delete(CurrentUser.get(), id);
        return ResponseEntity.noContent().build();
    }
}
