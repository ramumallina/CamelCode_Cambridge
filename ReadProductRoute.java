package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.ProductLookupTableProcessor;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class ReadProductRoute extends RouteBuilder {

  public static final Logger LOGGER = LoggerFactory.getLogger(ReadProductRoute.class);
  
  @Override
  public void configure() throws Exception {
    
    onException(Exception.class)
      .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
      .handled(true)
      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE)
    .end();
    
    onException(NullPointerException.class)
      .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
      .handled(true)
      .log(LoggingLevel.ERROR,LOGGER,"NullPointer Error:::"+ CEILConstants.LOG_ERROR_MESSAGE)
    .end();
    
    from(CEILConstants.DIRECT_READ_PRODUCT_ROUTE)
      .routeId(getClass().getName())
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START)
      .toD(CEILConstants.READ_PRODUCT_CRUD_QUERY)
      .choice()
        .when(PredicateBuilder.or(body().isEqualTo(""),body().isNull()))
          .throwException(new NullPointerException("Record(s) doesn't exists"))
        .otherwise()
          .bean(ProductLookupTableProcessor.class,"productLookupPayload")
          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value())) 
          .setBody(simple("${body}"))
    .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE)
    .end();
  }
}