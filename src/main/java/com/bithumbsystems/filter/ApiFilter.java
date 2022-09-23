package com.bithumbsystems.filter;

import com.bithumbsystems.config.Config;
import com.bithumbsystems.config.constant.GlobalConstant;
import com.bithumbsystems.exception.GatewayException;
import com.bithumbsystems.exception.GatewayExceptionHandler;
import com.bithumbsystems.exception.GatewayStatusException;
import com.bithumbsystems.model.enums.ErrorCode;
import com.bithumbsystems.request.TokenRequest;
import com.bithumbsystems.utils.CommonUtil;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Slf4j
@Component
public class ApiFilter extends AbstractGatewayFilterFactory<Config> {

  @Value("${sites.smart-admin-gateway-url}")
  private String smartAdminGatewayUrl;

  public ApiFilter() {
    super(Config.class);
  }

  @Bean
  public ErrorWebExceptionHandler exceptionHandler() {
    return new GatewayExceptionHandler();
  }

  @Override
  public GatewayFilter apply(final Config config) {
    return (exchange, chain) -> {
      log.info("ApiFilter called...");
      log.info("ApiFilter baseMessage: {}", config.getBaseMessage());

      if (config.isPreLogger()) {
        log.info("ApiFilter Start: {}", exchange.getRequest());
      }

      ServerHttpRequest request = exchange.getRequest();
      log.debug("header => {}", request.getHeaders());
      // 사용자 IP check
      String userIp = CommonUtil.getUserIp(request);
      log.debug("user IP => {}", userIp);

      String siteId = validateRequest(request);
      log.debug("site_id => {}", siteId);

      AtomicReference<String> goUrl = new AtomicReference<>(smartAdminGatewayUrl);

      // Header에 user_ip를 넣어야 한다.
      log.debug(exchange.getRequest().getURI().toString());
      log.debug(exchange.getRequest().getURI().getHost());
      log.debug(exchange.getRequest().getURI().getPath());
      log.debug(exchange.getRequest().getURI().getRawQuery());
      log.debug(exchange.getRequest().getURI().getRawPath());

      String replaceUrl = goUrl.get() + exchange.getRequest().getURI().getPath();
      if (StringUtils.hasLength(exchange.getRequest().getURI().getQuery())) {
        replaceUrl += "?" + exchange.getRequest().getURI().getRawQuery();
      }
      log.debug("replaceUrl:" + replaceUrl);

      return chain.filter(exchange).doOnError(e -> {
        log.error(e.getMessage());
        throw new GatewayException(ErrorCode.SERVER_RESPONSE_ERROR);
      }).then(Mono.fromRunnable(()-> {
        if (config.isPostLogger()) {
          log.info("UserFilter End: {}", exchange.getResponse());
        }
      }));

    };
  }

  private String validateRequest(ServerHttpRequest request) {
    log.debug("validation check start");
    // 사이트 코드 체크
    if (!request.getHeaders().containsKey(GlobalConstant.SITE_ID)) {
      log.debug(">>>>> SITE ID NOT CONTAINS <<<<<");
      log.debug(">>>>>HEADER => {}", request.getHeaders());
      log.debug(">>>>>URI => {}", request.getURI());
      throw new GatewayException(ErrorCode.INVALID_HEADER_SITE_ID);
    }
    // 사이트 코드에 따른 Authorization check
    String siteId = request.getHeaders().getFirst(GlobalConstant.SITE_ID);
    if (!StringUtils.hasLength(siteId)) {
      log.debug(">>>>> SITE ID NOT FOUND <<<<<");
      log.debug(">>>>> header => {}", request.getHeaders());
      log.debug(">>>>> URI => {}", request.getURI());
      log.debug(">>>>> siteId => {}", siteId);
      throw new GatewayException(ErrorCode.INVALID_HEADER_SITE_ID);
    }
    return siteId;
  }
}
