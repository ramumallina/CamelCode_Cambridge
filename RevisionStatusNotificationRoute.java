package com.ca.ceil.marking.svc.camelroutes;
/*
 * Author: Ramu Mallina RevisionStatusNotificationRoute Created for ELiT Priming
 * 
 */

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.zipfile.ZipSplitter;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.http.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.ElitPrimingMetaDataReading;
import com.ca.ceil.marking.svc.camelprocessor.GenerateMediaPayloadProcessor;
import com.ca.ceil.marking.svc.camelprocessor.GetUuid;
import com.ca.ceil.marking.svc.camelprocessor.ReadingCallBackUrl;
import com.ca.ceil.marking.svc.camelprocessor.ReadingElitQuestionXml;
import com.ca.ceil.marking.svc.camelprocessor.ReadingRevisionStatusUpdatedPayload;
import com.ca.ceil.marking.svc.camelprocessor.SignedResponseUrlReading;
import com.ca.ceil.marking.svc.camelprocessor.TokenReadingProcessor;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import com.ca.ceil.marking.svc.utility.URLConstants;

@Component
public class RevisionStatusNotificationRoute extends RouteBuilder {
  
  private static final Logger LOGGER = LoggerFactory.getLogger(RevisionStatusNotificationRoute.class);
  
  @Value("${exception.redelivery.api.delay}")
  private int maxRedeliveryDelay;
  
  @Value("${exception.redelivery.api.attempts}")
  private int maxRedeliveries;

  @Autowired
  TokenReadingProcessor tokenReadingProcessor;

  @Autowired
  ReadingElitQuestionXml readingElitQuestionXml;

  @Autowired
  ReadingRevisionStatusUpdatedPayload readingRevisionStatusUpdatedPayload;

  @Autowired
  ReadingCallBackUrl readingCallBackUrl;

  @Autowired
  SignedResponseUrlReading signedResponseUrlReading;

  @Autowired
  GetUuid getUuid;
  
  @Autowired
  GenerateMediaPayloadProcessor generateMediaPayload;
  
  @Autowired
  ElitPrimingMetaDataReading elitPrimingMetaDataReading;
  
  @SuppressWarnings("unchecked")
  @Override
  public void configure() throws Exception {
      
    onException(Exception.class)
      .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
      .handled(true)
      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE + " for contextObjectId: ${exchangeProperty.contextObjectId}")
    .end();
    
    onException(ConnectException.class, SocketTimeoutException.class, HttpOperationFailedException.class, HttpException.class)
      .maximumRedeliveries(maxRedeliveries)
      .redeliveryDelay(maxRedeliveryDelay)
      .handled(true)
      .log(LoggingLevel.ERROR,LOGGER,"Http Connection Error:::"+ CEILConstants.LOG_ERROR_MESSAGE + " for contextObjectId: ${exchangeProperty.contextObjectId}")
    .end();
      
    from(CEILConstants.DIRECT_REVISION_STATUS_NOTIFICATION_ROUTE)
        .routeId(getClass().getName())
        .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " for contextObjectId: ${exchangeProperty.contextObjectId}")
        .setBody(simple("${exchangeProperty.revisionStatusPayload}"))
        .log("RevisionStatusNotificationRoute WebHookNotification for questionContentItemId: ${exchangeProperty.contextObjectId} is :::${body}")
        .unmarshal().json(JsonLibrary.Jackson)
        .setProperty("contextObjectId",jsonpath("$.contextObjectId"))
        .setProperty("current_value",jsonpath("$.extraInfo.value"))
        .setProperty("previous_value",jsonpath("$.extraInfo.previousValue"))
        .setBody(constant(""))
        
        .setHeader(Exchange.CONTENT_TYPE, simple(CEILConstants.TOKEN_CONTENT_TYPE))
        .setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_POST))
        .toD(URLConstants.TOKEN_ENDPOINT_URL)
        .unmarshal().json(JsonLibrary.Jackson)
        .setHeader(CEILConstants.AUTHORIZATION_KEY, simple("Bearer ${body['access_token']}"))
        .setBody(simple(CEILConstants.ELIT_REVISION_CONTENT_ITEM_PAYLOAD))
        .marshal().json(JsonLibrary.Jackson)
        .unmarshal().json()
        .toD(URLConstants.ORDER_EXPORT_CONTENT)
        
        .unmarshal().json(JsonLibrary.Jackson)
        .setHeader("callbackUrl",jsonpath("$.callbackUrl"))
        .setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_GET))
        .delay(5000)
        .toD("${header.callbackUrl}")

        .removeHeaders("*")
        .process(signedResponseUrlReading)
        .setHeader(Exchange.HTTP_QUERY, simple("${header.queryParameterssignedResponseUrl}"))
        .toD("${header.urlStartingsignedResponseUrl}")
        
        .split(new ZipSplitter()).streaming().convertBodyTo(String.class)
        .choice()
          .when(body().isNotNull())
            .choice()
              .when(PredicateBuilder.or(body().contains("<manifest"),body().contains("<assessmentTest")))
                .toD("file:{{elit.priming.question.folder}}/extractedFiles2/${exchangeProperty.contextObjectId}")
              .otherwise()
                .toD("file:{{elit.priming.question.folder}}/extractedFiles/${exchangeProperty.contextObjectId}")
            .end()
          .endChoice()
        .end()
        .end()
        
        .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE  + " for contextObjectId: ${exchangeProperty.contextObjectId}")
        .end();
        }
}