package com.benggri.example.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.post.entity.Post;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LombokAccessorsTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Pattern HANDWRITTEN_GETTER =
            Pattern.compile("public\\s+[\\w<>\\[\\],.? ]+\\s+(get|is)[A-Z]\\w*\\s*\\(\\s*\\)\\s*\\{");
    private static final Pattern HANDWRITTEN_GET_OR_SET =
            Pattern.compile("public\\s+[\\w<>\\[\\],.? ]+\\s+(get|set)[A-Z]\\w*\\s*\\(");
    private static final Pattern HANDWRITTEN_SETTER =
            Pattern.compile("public\\s+void\\s+set[A-Z]\\w*\\s*\\(");

    private static final List<Path> ENTITY_SOURCES = List.of(
            MAIN.resolve("com/benggri/example/core/member/entity/Member.java"),
            MAIN.resolve("com/benggri/example/core/post/entity/Post.java"));

    @Test
    void entities_useLombokGetterAnnotation() throws IOException {
        for (Path source : ENTITY_SOURCES) {
            assertThat(source).exists();
            String text = Files.readString(source);
            assertThat(text).contains("@Getter");
            assertThat(HANDWRITTEN_GETTER.matcher(text).find())
                    .as("handwritten accessor in %s", source)
                    .isFalse();
        }
    }

    @Test
    void entities_haveNoPublicSetters() {
        for (Class<?> type : List.of(Member.class, Post.class)) {
            assertThat(Arrays.stream(type.getDeclaredMethods())
                    .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                    .map(Method::getName)
                    .filter(n -> n.startsWith("set")))
                    .as("public setters on %s", type.getSimpleName())
                    .isEmpty();
        }
    }

    @Test
    void main_hasNoHandwrittenAccessors() throws IOException {
        assertThat(MAIN).isDirectory();
        try (Stream<Path> files = Files.walk(MAIN)) {
            List<Path> sources = files.filter(p -> p.toString().endsWith(".java")).toList();
            assertThat(sources).isNotEmpty();
            for (Path source : sources) {
                String text = Files.readString(source);
                assertThat(HANDWRITTEN_GET_OR_SET.matcher(text).find())
                        .as("handwritten get/set in %s", source)
                        .isFalse();
                assertThat(HANDWRITTEN_SETTER.matcher(text).find())
                        .as("handwritten setter in %s", source)
                        .isFalse();
            }
        }
    }
}
