package com.benggri.example.graphql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** core lives in another package tree, so component scanning is widened (JPA scanning: PersistenceConfig). */
@SpringBootApplication(scanBasePackages = "com.benggri.example")
public class GraphqlApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphqlApplication.class, args);
    }
}
