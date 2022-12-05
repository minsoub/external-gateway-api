package com.bithumbsystems.filter;

import com.bithumbsystems.config.Config;
import com.bithumbsystems.config.constant.GlobalConstant;
import com.bithumbsystems.config.properties.UrlProperties;
import com.bithumbsystems.exception.GatewayException;
import com.bithumbsystems.exception.GatewayExceptionHandler;
import com.bithumbsystems.model.enums.ErrorCode;
import com.bithumbsystems.utils.CommonUtil;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

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
      log.debug("host => {}", request.getURI().getHost());
      // 사용자 IP check
      String userIp = CommonUtil.getUserIp(request);
      log.debug("user IP => {}", userIp);

      String siteId = validateRequest(request);
      log.debug("site_id => {}", siteId);

      validateDomains(config, siteId, request.getURI().getHost());

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
    // 접속 가능한 사이트 아이디만 검증.
    if (!siteId.equals(GlobalConstant.LRC_SITE_ID) && !siteId.equals(GlobalConstant.CPC_SITE_ID)) {
      throw new GatewayException(ErrorCode.INVALID_HEADER_SITE_ID);
    }
    return siteId;
  }

  /**
   * 접속 가능한 도메인 체크
   *
   * @param config
   * @param siteId
   * @param host
   */
  private void validateDomains(final Config config, String siteId, String host) {
    log.debug("validation check Domains... start");
    log.debug("check domain => {}", host);
    log.debug("allow host lrc list => {}", config.getAllowHostProperties().lrc);
    log.debug("allow host cpc list => {}", config.getAllowHostProperties().cpc);
    // host : localhost, safe.bithumbsystems.com

    if (siteId.equals(GlobalConstant.LRC_SITE_ID)) {
      if (!config.getAllowHostProperties().lrc.stream().anyMatch( x -> x.indexOf(host) != -1)) {
        throw new GatewayException(ErrorCode.INVALID_DOMAIN);
      }
    } else if(siteId.equals(GlobalConstant.CPC_SITE_ID)) {
      if (!config.getAllowHostProperties().cpc.stream().anyMatch( x -> x.indexOf(host) != -1)) {
        throw new GatewayException(ErrorCode.INVALID_DOMAIN);
      }
    } else {
      throw new GatewayException(ErrorCode.INVALID_HEADER_SITE_ID);
    }
  }
}
