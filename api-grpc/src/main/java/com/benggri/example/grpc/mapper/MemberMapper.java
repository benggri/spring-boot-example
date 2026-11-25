package com.benggri.example.grpc.mapper;

import com.benggri.example.core.member.dto.MemberResult;
import com.benggri.example.core.security.TokenPair;
import com.benggri.example.grpc.proto.MemberResponse;
import com.benggri.example.grpc.proto.TokenResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** core results to proto responses; protos never carry entities. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MemberMapper {

    public static MemberResponse toResponse(MemberResult member) {
        return MemberResponse.newBuilder()
                .setId(member.id())
                .setEmail(member.email())
                .setName(member.name())
                .setRole(member.role().name())
                .build();
    }

    public static TokenResponse toResponse(TokenPair tokens) {
        return TokenResponse.newBuilder()
                .setAccessToken(tokens.accessToken())
                .setRefreshToken(tokens.refreshToken())
                .build();
    }
}
