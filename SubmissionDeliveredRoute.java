package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.CODE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DIRECT_SUBMISSION_DELIVERED_ROUTE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.SUBMISSION_ASSOCIATED_OBJECT_ID;
import static com.ca.ceil.marking.svc.utility.URLConstants.TOKEN_ENDPOINT_URL;
import static com.ca.ceil.marking.svc.utility.CEILConstants.SUBMISSION_CONTEXT_OBJECT_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.SUBMISSION_DELIVERED_EXPORT_ITEM_PAYLOAD;
import static com.ca.ceil.marking.svc.utility.CEILConstants.AUTHORIZATION_KEY;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.http.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.DeliveredNotificationReading;
import com.ca.ceil.marking.svc.camelprocessor.PreparingExportFinishedJson;
import com.ca.ceil.marking.svc.camelprocessor.TokenReadingProcessor;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import com.ca.ceil.marking.svc.utility.URLConstants;

@Component
public class SubmissionDeliveredRoute extends RouteBuilder {
  
private static final Logger LOGGER = LoggerFactory.getLogger(SubmissionDeliveredRoute.class);
  
  @Value("${exception.redelivery.api.delay}")
  private int maxRedeliveryDelay;
  
  @Value("${exception.redelivery.api.attempts}")
  private int maxRedeliveries;

  @Autowired
  TokenReadingProcessor tokenReadingProcessor;

  @Autowired
  DeliveredNotificationReading deliveredNotificationReading;

  @Autowired
  PreparingExportFinishedJson preparingExportFinishedJson;

  @SuppressWarnings("unchecked")
  @Override
  public void configure() throws Exception {

    onException(ConnectException.class, SocketTimeoutException.class, HttpOperationFailedException.class, HttpException.class)
      .maximumRedeliveries(maxRedeliveries)
      .redeliveryDelay(maxRedeliveryDelay)
      .handled(true)
      .log(LoggingLevel.ERROR,LOGGER,"for associatedObjectId: ${header.associatedObjectId} " + CEILConstants.LOG_ERROR_MESSAGE)
      .end();

    onException(Exception.class)
      .handled(true)
      .log(LoggingLevel.ERROR,LOGGER,"for associatedObjectId: ${header.associatedObjectId} " + CEILConstants.LOG_ERROR_MESSAGE)
      .end();

    from(DIRECT_SUBMISSION_DELIVERED_ROUTE)
        .routeId(getClass().getName())
        .setHeader(SUBMISSION_ASSOCIATED_OBJECT_ID, jsonpath("$.associatedObjectId"))
        .setHeader(SUBMISSION_CONTEXT_OBJECT_ID, jsonpath("$.contextObjectId"))
        .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " for associatedObjectId: ${header.associatedObjectId}")
        .setBody(constant(""))
        .convertBodyTo(String.class)
        .setHeader(Exchange.CONTENT_TYPE, simple(CEILConstants.TOKEN_CONTENT_TYPE))
        .setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_POST))
        .toD(TOKEN_ENDPOINT_URL)
        .unmarshal().json(JsonLibrary.Jackson)
        .setProperty(AUTHORIZATION_KEY, simple("Bearer ${body['access_token']}"))
        .setBody(simple(SUBMISSION_DELIVERED_EXPORT_ITEM_PAYLOAD))
        .removeHeaders("*")
        .setHeader(AUTHORIZATION_KEY, simple("${exchangeProperty.Authorization}"))
        .setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_POST))
        .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
        .toD(URLConstants.ORDER_EXPORT_CONTENT)
        .log(LoggingLevel.INFO, LOGGER, "Body After ORDER_EXPORT_SUBMISSION is:::${body}")
        .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE  + " for associatedObjectId: ${header.associatedObjectId}")
     .end();
  }
}
