package com.benggri.example.grpc.service;

import com.benggri.example.core.member.service.MemberService;
import com.benggri.example.grpc.interceptor.AuthContext;
import com.benggri.example.grpc.mapper.MemberMapper;
import com.benggri.example.grpc.proto.DeleteMemberRequest;
import com.benggri.example.grpc.proto.DeleteMemberResponse;
import com.benggri.example.grpc.proto.GetMemberRequest;
import com.benggri.example.grpc.proto.MemberResponse;
import com.benggri.example.grpc.proto.MemberServiceGrpc;
import com.benggri.example.grpc.proto.RegisterRequest;
import com.benggri.example.grpc.proto.UpdateMemberRequest;
import com.benggri.example.grpc.validation.RequestValidator;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

/** Authorization (self or admin) is enforced by core; the actor comes from the gRPC Context. */
@GrpcService
@RequiredArgsConstructor
public class MemberGrpcService extends MemberServiceGrpc.MemberServiceImplBase {

    private final MemberService memberService;

    @Override
    public void register(RegisterRequest request, StreamObserver<MemberResponse> observer) {
        RequestValidator.register(request);
        observer.onNext(MemberMapper.toResponse(
                memberService.register(request.getEmail(), request.getPassword(), request.getName())));
        observer.onCompleted();
    }

    @Override
    public void get(GetMemberRequest request, StreamObserver<MemberResponse> observer) {
        RequestValidator.id(request.getId());
        observer.onNext(MemberMapper.toResponse(memberService.get(AuthContext.current(), request.getId())));
        observer.onCompleted();
    }

    @Override
    public void update(UpdateMemberRequest request, StreamObserver<MemberResponse> observer) {
        RequestValidator.id(request.getId());
        RequestValidator.memberName(request.getName());
        observer.onNext(MemberMapper.toResponse(
                memberService.update(AuthContext.current(), request.getId(), request.getName())));
        observer.onCompleted();
    }

    @Override
    public void delete(DeleteMemberRequest request, StreamObserver<DeleteMemberResponse> observer) {
        RequestValidator.id(request.getId());
        memberService.delete(AuthContext.current(), request.getId());
        observer.onNext(DeleteMemberResponse.getDefaultInstance());
        observer.onCompleted();
    }
}
