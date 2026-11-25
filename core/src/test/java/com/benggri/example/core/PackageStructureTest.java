package com.benggri.example.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PackageStructureTest {

    private static final String BASE = "com.benggri.example.core.";

    @Test
    void structure_hasExpectedClasses() throws ClassNotFoundException {
        List<String> names = List.of(
                "member.entity.Member",
                "member.entity.Role",
                "member.repository.MemberRepository",
                "member.service.MemberService",
                "member.dto.MemberResult",
                "post.entity.Post",
                "post.repository.PostRepository",
                "post.repository.PostQueryRepository",
                "post.repository.PostQueryRepositoryImpl",
                "post.service.PostService",
                "post.dto.PostResult",
                "post.dto.PostSearchCondition");
        for (String name : names) {
            assertThat(Class.forName(BASE + name)).isNotNull();
        }
    }

    @Test
    void structure_featureFoldersContainOnlyLayerFolders() throws IOException {
        for (String feature : List.of("member", "post")) {
            Path dir = Path.of("src/main/java/com/benggri/example/core/" + feature);
            assertThat(dir).isDirectory();
            try (Stream<Path> children = Files.list(dir)) {
                assertThat(children.filter(p -> p.toString().endsWith(".java"))).isEmpty();
            }
        }
    }
}
