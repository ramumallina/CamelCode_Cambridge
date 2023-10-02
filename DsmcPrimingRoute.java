package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.DIRECT_DSMC_PRIMING_ROUTE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DSMC_PRIMING_SELECT_QUERY_URL;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.GenerateDsmcPrimingPayload;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class DsmcPrimingRoute extends RouteBuilder{
  
  private static final Logger LOGGER = LoggerFactory.getLogger(DsmcPrimingRoute.class);
  
  @Value("${exception.redelivery.api.delay}")
  private int maxRedeliveryDelay;
  
  @Value("${exception.redelivery.api.attempts}")
  private int maxRedeliveries;
  
  @Override
  public void configure() throws Exception {
    
    onException(Exception.class)
    .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
    .handled(true)
    .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE)
    .end();
    
    from(DIRECT_DSMC_PRIMING_ROUTE)
      .routeId(getClass().getName())
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START)
      .to(DSMC_PRIMING_SELECT_QUERY_URL)
      .choice()
        .when(PredicateBuilder.or(body().isEqualTo(""),body().isNull()))
          .throwException(new NullPointerException("No records are there for dsmc_priming"))
        .otherwise()
            .bean(GenerateDsmcPrimingPayload.class,"dsmcPrimingPayload")
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
            .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE)
      .end();
  }
}
