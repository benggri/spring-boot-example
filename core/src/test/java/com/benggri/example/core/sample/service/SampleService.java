package com.benggri.example.core.sample.service;

import com.benggri.example.core.aop.Audited;
import com.benggri.example.core.security.AuthUser;

/** Test-only stand-in for a real service; matches the logging pointcut by package and name. */
public class SampleService {

    public String greet(String name) {
        return "hello " + name;
    }

    @Audited("sample.act")
    public String act(AuthUser actor, String arg) {
        return "done:" + arg;
    }

    @Audited("sample.fail")
    public void fail(AuthUser actor, RuntimeException toThrow) {
        throw toThrow;
    }

    @Audited("sample.anon")
    public String anon(String arg) {
        return "anon:" + arg;
    }

    @Audited("sample.register")
    public String register(String email, String rawPassword, String name) {
        return "registered";
    }
}
