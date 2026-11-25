package com.benggri.example.core.aop;

import com.benggri.example.core.security.AuthUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Audit trail for {@link Audited} methods. Records action, actor id and outcome only: no argument
 * values, and only the exception class name because messages may contain emails.
 */
@Aspect
@Order(AopOrder.AUDIT)
@Component
public class AuditAspect {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit");
    private static final String ANONYMOUS = "anonymous";

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        String actor = actorOf(joinPoint.getArgs());
        try {
            Object result = joinPoint.proceed();
            AUDIT.info("action={} actor={} outcome=SUCCESS", audited.value(), actor);
            return result;
        } catch (Throwable t) {
            AUDIT.info("action={} actor={} outcome=FAILURE({})",
                    audited.value(), actor, t.getClass().getSimpleName());
            throw t;
        }
    }

    private static String actorOf(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof AuthUser user) {
                return String.valueOf(user.id());
            }
        }
        return ANONYMOUS;
    }
}
