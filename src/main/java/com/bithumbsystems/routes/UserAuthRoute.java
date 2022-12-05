package com.bithumbsystems.routes;

import com.bithumbsystems.config.Config;
import com.bithumbsystems.config.properties.AllowHostProperties;
import com.bithumbsystems.config.properties.UrlProperties;
import com.bithumbsystems.filter.ApiFilter;
import com.bithumbsystems.filter.UserFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
@RequiredArgsConstructor
public class UserAuthRoute {

    private final UrlProperties urlProperties;
    private final AllowHostProperties allowHostProperties;

    private final UserFilter userFilter;
    private final ApiFilter apiFilter;

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        log.debug(urlProperties.getSmartAdminGatewayUrl());
        return builder.routes()
                .route("user-service",   // 운영자 로그인 처리
                        route -> route.path("/user/**")
                                .filters(filter -> filter.filter(userFilter.apply(new Config("UserFilter apply", allowHostProperties, true, true))))
                                .uri(urlProperties.getSmartAdminGatewayUrl())
                )
                // debug
                .route("adm-service",   // 운영자 로그인 처리
                        route -> route.path("/adm/**")
                                .filters(filter -> filter.filter(userFilter.apply(new Config("UserFilter apply", allowHostProperties, true, true))))
                                .uri(urlProperties.getSmartAdminGatewayUrl())
                )
                .route("api-service-mng",   // 고객보호 API 서비스 호출
                        route -> route.path("/api/**")
                                .filters(filter -> filter.filter(apiFilter.apply(new Config("MNG ApiFilter apply", allowHostProperties, true, true))))
                                .uri(urlProperties.getSmartAdminGatewayUrl())
                )
                // debug end
                .route("api-service-cpc",   // 고객보호 API 서비스 호출
                        route -> route.path("/api/*/cpc/**")
                                    .filters(filter -> filter.filter(apiFilter.apply(new Config("CPC ApiFilter apply", allowHostProperties, true, true))))
                                .uri(urlProperties.getSmartAdminGatewayUrl())
                )
                .route("api-service-lrc",   // 거래지원 API 서비스 호출
                        route -> route.path("/api/*/lrc/**")
                                .filters(filter -> filter.filter(apiFilter.apply(new Config("LRC ApiFilter apply", allowHostProperties, true, true))))
                                .uri(urlProperties.getSmartAdminGatewayUrl())
                )
                .build();
    }
}
