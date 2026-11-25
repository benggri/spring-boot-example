package com.benggri.example.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.grpc.proto.Auth;
import com.benggri.example.grpc.proto.Member;
import com.benggri.example.grpc.proto.MemberServiceGrpc;
import com.benggri.example.grpc.proto.PostResponse;
import com.benggri.example.grpc.proto.Post;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Descriptor-level guards: a post never exposes the author's email, no response exposes a password. */
class ProtoPrivacyTest {

    private static final Set<String> SECRET_FIELDS = Set.of("password", "password_hash");

    @Test
    void postMessage_hasNoEmailOrPasswordFields() {
        Set<Descriptor> reachable = new HashSet<>();
        collect(PostResponse.getDescriptor(), reachable);
        assertThat(reachable).extracting(Descriptor::getName).contains("PostResponse", "Author");
        for (Descriptor message : reachable) {
            for (FieldDescriptor field : message.getFields()) {
                assertThat(field.getName()).isNotIn("email", "role", "password", "password_hash");
            }
        }
    }

    @Test
    void responseMessages_haveNoPasswordFields() {
        Set<Descriptor> reachable = new HashSet<>();
        for (ServiceDescriptor service : services()) {
            service.getMethods().forEach(m -> collect(m.getOutputType(), reachable));
        }
        assertThat(reachable).isNotEmpty();
        for (Descriptor message : reachable) {
            for (FieldDescriptor field : message.getFields()) {
                assertThat(field.getName()).isNotIn(SECRET_FIELDS);
            }
        }
    }

    @Test
    void memberService_hasNoListRpc() {
        assertThat(MemberServiceGrpc.getServiceDescriptor().getMethods())
                .extracting(m -> m.getBareMethodName())
                .containsExactlyInAnyOrder("Register", "Get", "Update", "Delete");
    }

    private static List<ServiceDescriptor> services() {
        return Stream.of(Auth.getDescriptor(), Member.getDescriptor(), Post.getDescriptor())
                .flatMap(file -> file.getServices().stream()).toList();
    }

    private static void collect(Descriptor message, Set<Descriptor> seen) {
        if (!seen.add(message)) {
            return;
        }
        for (FieldDescriptor field : message.getFields()) {
            if (field.getType() == FieldDescriptor.Type.MESSAGE) {
                collect(field.getMessageType(), seen);
            }
        }
    }
}
