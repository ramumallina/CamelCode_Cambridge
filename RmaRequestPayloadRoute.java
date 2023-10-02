package com.ca.ceil.marking.svc.camelroutes;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.http.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.GenerateJsonPayloadProcessor;
import com.ca.ceil.marking.svc.camelprocessor.GenerateUniqueIdentifier;
import com.ca.ceil.marking.svc.camelprocessor.GenerateUuid;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import com.ca.ceil.marking.svc.utility.URLConstants;

@Component
public class RmaRequestPayloadRoute extends RouteBuilder{
    
  private static final Logger LOGGER = LoggerFactory.getLogger(RmaRequestPayloadRoute.class);
  
  @Value("${exception.redelivery.api.delay}")
  private int maxRedeliveryDelay;
  
  @Value("${exception.redelivery.api.attempts}")
  private int maxRedeliveries;
  
@SuppressWarnings("unchecked")
@Override
  public void configure() throws Exception {
    
  onException(ConnectException.class, SocketTimeoutException.class, HttpOperationFailedException.class, HttpException.class)
  .maximumRedeliveries(maxRedeliveries)
  .redeliveryDelay(maxRedeliveryDelay)
  .handled(true)
  .log(LoggingLevel.ERROR,LOGGER,"Http Connection Error:::"+ CEILConstants.LOG_ERROR_MESSAGE+ " For Candidate Id: ${exchangeProperty.candidateId}")
.end();
          
    onException(Exception.class)
    .handled(true)
    .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE+ " For Candidate Id: ${exchangeProperty.candidateId}")
  .end(); 
    
    from(CEILConstants.DIRECT_RMA_DIGITAL_RESPONSE_ROUTE) 
        .routeId(getClass().getName())
        .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START+ " For Candidate Id: ${exchangeProperty.candidateId}")
        .setHeader(CEILConstants.QUESTION_ID, simple("${exchangeProperty.questionId}"))
        .setProperty(CEILConstants.RMA_HTML_FILE_IDENTIFIER, simple("${exchangeProperty.rmahtmlfileIdentifier}"))
        .setHeader(CEILConstants.QUESTION_TITLE, simple("${exchangeProperty.questionTitle}"))
        .setHeader(CEILConstants.CANDIDATE_ID, simple("${exchangeProperty.candidateId}"))
        
        .setHeader("qId", simple("${header.questionId}", Integer.class))
        .toD(CEILConstants.RMA_BRIEFING_COMMENTS_SELECT_QUERY_URL)
        .bean(GenerateJsonPayloadProcessor.class,"readBriefingComment")
        
        .toD(CEILConstants.SQL_QUERY_SELECT_RMA_DIGITAL_RESPONSE_CANDIDATE_DETAILS)
        .choice()
          .when(PredicateBuilder.or(body().isEqualTo(""),body().isNull()))
            .throwException(new NullPointerException("Candidate Info is not present in Candidate Details Table"))
          .otherwise()
            .bean(GenerateJsonPayloadProcessor.class,"candidateDetailsPayloadRMA")
            .toD(CEILConstants.SQL_QUERY_SELECT_RMA_DIGITAL_RESPONSE)
            .choice() 
                .when(PredicateBuilder.or(body().isEqualTo(""),body().isNull()))
                    .throwException(new NullPointerException("Priming is not done for the record"))
                .otherwise()
                    .bean(GenerateUuid.class,"generateUniqueCourseworkResponseIdentifier")
                    .bean(GenerateUniqueIdentifier.class,"generateResponseFileIdentifier")
                    .bean(GenerateJsonPayloadProcessor.class,"rmaRequestPayload")
                    .setProperty(CEILConstants.PAYLOAD_BODY, simple("${body}"))
                    
                    .removeHeaders("*")
                    .setHeader(Exchange.CONTENT_TYPE, simple(CEILConstants.TOKEN_CONTENT_TYPE))
                    .setBody(simple(CEILConstants.HEADERS_RMA_TOKEN_ENDPOINT_URL))
                    .toD(URLConstants.RMA_TOKEN_ENDPOINT_URL)
                    .unmarshal().json(JsonLibrary.Jackson)
                    .setHeader(CEILConstants.AUTHORIZATION_KEY, simple("Bearer ${body['access_token']}"))
                    
                    .setHeader(CEILConstants.AUTHORIZATION_KEY, simple(CEILConstants.AUTHORIZATION_HEADER))
                    .setHeader(Exchange.CONTENT_TYPE, constant(CEILConstants.CONTENT_TYPE))
                    .setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_POST))
                    .setBody(simple("${exchangeProperty.payloadBody}"))
                    .setHeader(CEILConstants.TEST_IDENTIFIER, simple("${exchangeProperty.testIdentifier}"))
                    .setHeader(CEILConstants.RMA_HTML_FILE_IDENTIFIER, simple("${exchangeProperty.rmaFileIdentifier}"))
                    .log(LoggingLevel.INFO,LOGGER,"Body Before Sending To RMA System For Candidate Id: ${exchangeProperty.candidateId} is:::${body}")
                    .toD(URLConstants.RMA_SYSTEM_TARGET_ENDPOINT_URL)
                    .log(LoggingLevel.INFO,LOGGER,"RMA Digital Response Status Code For Candidate Id: ${exchangeProperty.candidateId} is:::${in.header.CamelHttpResponseCode}")
                    .log(LoggingLevel.INFO,LOGGER,"Body After Sending To RMA System For Candidate Id: ${exchangeProperty.candidateId} is:::${body}")
                    .toD(CEILConstants.SQL_QUERY_UPDATE_RMA_DIGITAL_RESPONSE_STATUS)
           .end()
       .end()
       .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE+ " For Candidate Id: ${exchangeProperty.candidateId}")
    .end(); 
  }
}