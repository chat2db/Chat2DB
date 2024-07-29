package ai.chat2db.server.web.start.config.config;

import ai.chat2db.server.domain.api.enums.RoleCodeEnum;
import ai.chat2db.server.domain.api.enums.ValidStatusEnum;
import ai.chat2db.server.domain.api.model.User;
import ai.chat2db.server.domain.api.service.TeamUserService;
import ai.chat2db.server.domain.api.service.UserService;
import ai.chat2db.server.domain.core.cache.CacheKey;
import ai.chat2db.server.domain.core.cache.MemoryCacheManage;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.tools.base.constant.SymbolConstant;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.common.config.Chat2dbProperties;
import ai.chat2db.server.tools.common.enums.ModeEnum;
import ai.chat2db.server.tools.common.exception.PermissionDeniedBusinessException;
import ai.chat2db.server.tools.common.exception.RedirectBusinessException;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.tools.common.util.I18nUtils;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaFoxUtil;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.Enumeration;

/**
 * web project configuration
 *
 * @author Shi Yi
 */
@Configuration
@Slf4j
public class Chat2dbWebMvcConfigurer implements WebMvcConfigurer {

    /**
     * api prefix
     */
    private static final String API_PREFIX = "/api/";

    /**
     * Globally released url
     */
    private static final String[] FRONT_PERMIT_ALL = new String[] {"/favicon.ico", "/error", "/static/**",
        "/api/system", "/login", "/api/system/get_latest_version"};

    @Resource
    private UserService userService;
    @Resource
    private TeamUserService teamUserService;
    @Resource
    private Chat2dbProperties chat2dbProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // All requests try to add user information
        registry.addInterceptor(new AsyncHandlerInterceptor() {
                @Override
                public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                    @NotNull Object handler) {
                    Dbutils.setSession();
                    String userIdString = (String)StpUtil.getLoginIdDefaultNull();
                    Long userId;
                    // Not logged in
                    if (!StringUtils.isNumeric(userIdString)) {
                        if (chat2dbProperties.getMode() == ModeEnum.DESKTOP) {
                            userId = RoleCodeEnum.DESKTOP.getDefaultUserId();
                        } else {
                            return true;
                        }
                    } else {
                        userId = Long.parseLong(userIdString);
                    }
                    Long finalUserId = userId;
                    LoginUser loginUser = MemoryCacheManage.computeIfAbsent(CacheKey.getLoginUserKey(userId), () -> {
                        User user = userService.query(finalUserId).getData();
                        if (user == null) {
                            return null;
                        }
                        if (!ValidStatusEnum.VALID.getCode().equals(user.getStatus())) {
                            StpUtil.logout();
                            throw new BusinessException("oauth.invalidUserName");
                        }
                        boolean admin = RoleCodeEnum.ADMIN.getCode().equals(user.getRoleCode());

                        return LoginUser.builder()
                            .id(user.getId())
                            .nickName(user.getNickName())
                            .admin(admin)
                            .roleCode(user.getRoleCode())
                            .build();
                    });

                    if (loginUser == null) {
                        // Indicates that the user may have been deleted
                        return true;
                    }

                    loginUser.setToken(StpUtil.getTokenValue());
                    ContextUtils.setContext(Context.builder()
                        .loginUser(loginUser)
                        .build());
                    return true;
                }

                @Override
                public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                    Exception ex) throws Exception {
                    // Remove login information
                    ContextUtils.removeContext();
                    Dbutils.removeSession();
                }
            })
            .order(1)
            .addPathPatterns("/**")
            .excludePathPatterns(FRONT_PERMIT_ALL);

        // Verify login information
        registry.addInterceptor(new AsyncHandlerInterceptor() {
                @Override
                public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                    @NotNull Object handler) throws IOException {
                    Context context = ContextUtils.queryContext();
                    // Verify login information
                    if (context == null) {
                        log.info("Login is required to access {},{}", buildHeaderString(request), SaHolder.getRequest().getUrl());

                        String path = SaHolder.getRequest().getRequestPath();
//                        if(path.startsWith("/login")){
//                            return true;
//                        }
                        if (path.startsWith(API_PREFIX)) {
                            response.getWriter().println(JSON.toJSONString(
                                ActionResult.fail("common.needLoggedIn", I18nUtils.getMessage("common.needLoggedIn"),
                                    "")));
                            return false;
                        } else {
                            throw new RedirectBusinessException(
                                "/login?callback=" + SaFoxUtil.joinParam(request.getRequestURI(),
                                    request.getQueryString()));
                        }
                    }
                    return true;
                }
            })
            .order(2)
            .addPathPatterns("/**")
            // Links that need to be released on the front end
            .excludePathPatterns(FRONT_PERMIT_ALL)
            // Uniform release ending in -a
            .excludePathPatterns("/**/*-a")
            // Uniform release of endings in _a
            .excludePathPatterns("/**/*_a");

        // Verify permissions
        registry.addInterceptor(new AsyncHandlerInterceptor() {
                @Override
                public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                    @NotNull Object handler) throws IOException {
                    LoginUser loginUser = ContextUtils.getLoginUser();
                    if (BooleanUtils.isNotTrue(loginUser.getAdmin())) {
                        throw new PermissionDeniedBusinessException();
                    }
                    return true;
                }
            })
            .order(3)
            .addPathPatterns("/api/admin/**")
            .addPathPatterns("/admin/**")
        ;

    }

    private String buildHeaderString(HttpServletRequest request) {
        StringBuilder stringBuilder = new StringBuilder();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            stringBuilder.append(headerName);
            stringBuilder.append(SymbolConstant.COLON);
            stringBuilder.append(request.getHeader(headerName));
            stringBuilder.append(SymbolConstant.COMMA);
        }
        return stringBuilder.toString();
    }
}
