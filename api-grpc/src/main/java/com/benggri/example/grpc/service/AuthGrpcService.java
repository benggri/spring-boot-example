package com.benggri.example.grpc.service;

import com.benggri.example.core.security.AuthService;
import com.benggri.example.grpc.mapper.MemberMapper;
import com.benggri.example.grpc.proto.AuthServiceGrpc;
import com.benggri.example.grpc.proto.LoginRequest;
import com.benggri.example.grpc.proto.RefreshRequest;
import com.benggri.example.grpc.proto.TokenResponse;
import com.benggri.example.grpc.validation.RequestValidator;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private final AuthService authService;

    @Override
    public void login(LoginRequest request, StreamObserver<TokenResponse> observer) {
        RequestValidator.login(request);
        observer.onNext(MemberMapper.toResponse(authService.login(request.getEmail(), request.getPassword())));
        observer.onCompleted();
    }

    @Override
    public void refresh(RefreshRequest request, StreamObserver<TokenResponse> observer) {
        RequestValidator.refresh(request);
        observer.onNext(MemberMapper.toResponse(authService.refresh(request.getRefreshToken())));
        observer.onCompleted();
    }
}
