package ai.chat2db.server.web.api.aspect;


import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class GatewayClientServiceAspect {
    /**
     *     定义切点，匹配 GatewayClientService 类中的所有方法
     */
    @Pointcut("execution(* ai.chat2db.server.web.api.http.GatewayClientService.*(..)) && !execution(* ai.chat2db.server.web.api.http.GatewayClientService.checkInWhite(..))")
    public void gatewayClientServiceMethods() {}



    /**
     * 环绕通知：在切点方法执行时触发
     * @param joinPoint
     * @return
     * @throws Throwable
     */
    @Around("gatewayClientServiceMethods()")
    public Object aroundGatewayClientServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        // 这里你可以执行一些自定义的逻辑，如果需要的话
        // 然后返回 null 或其他默认值
        return null;
    }
}
