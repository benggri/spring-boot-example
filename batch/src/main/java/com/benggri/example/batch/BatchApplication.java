package com.benggri.example.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Scans only this module: core contributes entities and repositories (see PersistenceConfig),
 * never its services, aspects or JwtConfig, so a batch run needs no JWT secret.
 */
@SpringBootApplication(scanBasePackages = "com.benggri.example.batch")
public class BatchApplication {

    public static void main(String[] args) {
        // SpringApplication.exit turns a failed job into a non-zero process exit code
        System.exit(SpringApplication.exit(SpringApplication.run(BatchApplication.class, args)));
    }
}
