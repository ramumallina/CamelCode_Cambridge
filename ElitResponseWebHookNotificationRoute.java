package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_ANSWER_UUID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_API_ROUTE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_WEBHOOK_NOTIFICATION;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jsonvalidator.JsonValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import com.fasterxml.jackson.core.JsonParseException;
import com.jayway.jsonpath.InvalidJsonException;
import org.apache.camel.ExpressionEvaluationException;

@Component
public class ElitResponseWebHookNotificationRoute extends RouteBuilder{
  
  public static final Logger LOGGER = LoggerFactory.getLogger(ElitResponseWebHookNotificationRoute.class);

  @SuppressWarnings("unchecked")
  @Override
  public void configure() throws Exception {
    
    onException(Exception.class)
      .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
      .handled(true)
      .log(LoggingLevel.ERROR,LOGGER,"for answerUuid: ${header.answer_uuid} " + CEILConstants.LOG_ERROR_MESSAGE);
  
    onException(JsonValidationException.class, ValidationException.class,JsonParseException.class,InvalidJsonException.class,ExpressionEvaluationException.class)
      .log(LoggingLevel.ERROR,LOGGER,"Validation Error for answerUuid: ${header.answer_uuid} :::"+CEILConstants.LOG_ERROR_MESSAGE)
      .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
      .handled(true);
    
    from(ELIT_RESPONSE_WEBHOOK_NOTIFICATION)
        .routeId(getClass().getName())
        .setHeader(ELIT_RESPONSE_ANSWER_UUID, jsonpath("$.notification.results.parts[0].answers[0].id"))
        .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " for answerUuid: ${header.answer_uuid}")
        .log(LoggingLevel.INFO,LOGGER, "Received Notification from ELiT:::${body}")
        .to("json-validator:ELiTResponseWebHookNotificationSchema.json")
        .log(LoggingLevel.INFO, LOGGER, "After Json Schema Validation,Sending to log endpoint:: Ended")
        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
        .wireTap(ELIT_RESPONSE_API_ROUTE)
        .bean(ResponseMessageDto.class,"setResponseMessage(${exchange}, ${exception})")
        .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE  + " for answerUuid: ${header.answer_uuid}")
        .end();
  }
}
