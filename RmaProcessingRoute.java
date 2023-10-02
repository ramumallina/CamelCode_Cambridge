package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.AUTHORIZATION_KEY;
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
import com.ca.ceil.marking.svc.camelprocessor.ReadingFileName;
import com.ca.ceil.marking.svc.camelprocessor.ReadingQuestionXml;
import com.ca.ceil.marking.svc.camelprocessor.SignedResponseUrlReading;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import com.ca.ceil.marking.svc.utility.URLConstants;
@Component
public class RmaProcessingRoute extends RouteBuilder{
  
  private static final Logger LOGGER = LoggerFactory.getLogger(RmaProcessingRoute.class);
  
  @Value("${exception.redelivery.api.delay}")
  private int maxRedeliveryDelay;
  
  @Value("${exception.redelivery.api.attempts}")
  private int maxRedeliveries;
  
  @Autowired
  ReadingQuestionXml readingQuestionXml;
  
  @Autowired
  SignedResponseUrlReading signedResponseUrlReading;

  @Autowired
  ReadingFileName readingFileName;

  @SuppressWarnings("unchecked")
  @Override
  public void configure() throws Exception {
    
    onException(ConnectException.class, SocketTimeoutException.class, HttpOperationFailedException.class, HttpException.class)
      .logRetryAttempted(true)
      .retryAttemptedLogLevel(LoggingLevel.WARN)
      .maximumRedeliveries(maxRedeliveries)
      .redeliveryDelay(maxRedeliveryDelay)
      .handled(true)
      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE+ " For Candidate Id: ${exchangeProperty.candidateId}")
      .end();
            
      onException(Exception.class)
        .handled(true)
        .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE+ " For Candidate Id: ${exchangeProperty.candidateId}")
        .end(); 
    
    from(CEILConstants.DIRECT_RMA_PROCESSING)
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START+ " For Candidate Id: ${exchangeProperty.candidateId}")
      .routeId(getClass().getName())
      .setProperty(CEILConstants.RMA_PRIMING_QUESTION_ID, simple(CEILConstants.RMA_QUESTIONID_BODY))
      .setProperty(CEILConstants.RMA_PRIMING_QUESTION_CONTENT_ITEMID, simple(CEILConstants.RMA_QUESTIONCONTENT_ITEMID_BODY))
      .setProperty(CEILConstants.RMA_PRIMING_CANDIDATE_ANSWER, simple(CEILConstants.RMA_CANDIDATE_ANSWER_BODY))
      .setProperty(CEILConstants.RMA_PRIMING_HTML_FILE_IDENTIFIER, simple(CEILConstants.RMA_HTMLFILE_IDENTIFIER_BODY))
      .setProperty(CEILConstants.RMA_PRIMING_PRODUCT_SOURCE_IDENTIFIER, simple(CEILConstants.RMA_PRODUCT_SOURCEIDENTIFIER_BODY))
      .setProperty("questionTitle", simple("${body.questionTitle}"))
      .setBody().simple("")
      
      .removeHeaders("*")
      .setHeader(Exchange.CONTENT_TYPE, simple(CEILConstants.TOKEN_CONTENT_TYPE))
      .setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_POST))
      .to(URLConstants.TOKEN_ENDPOINT_URL)
      
      .unmarshal().json(JsonLibrary.Jackson)
      .setHeader(AUTHORIZATION_KEY, simple("Bearer ${body['access_token']}"))
      .setProperty("Authorization", simple("${header.Authorization}"))
      .setBody(simple(CEILConstants.RMA_CONTENT_ITEM_PAYLOAD))
      .removeHeaders("*")
      .marshal().json(JsonLibrary.Jackson)
      .unmarshal().json()
      .setHeader(AUTHORIZATION_KEY, simple("${exchangeProperty.Authorization}"))
      .setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_POST))
      .setHeader(Exchange.CONTENT_TYPE, constant(CEILConstants.CONTENT_TYPE))
      .to(URLConstants.ORDER_EXPORT_CONTENT)
      .unmarshal().json(JsonLibrary.Jackson)
      .setHeader("callbackUrl",jsonpath("$.callbackUrl"))
      .setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_GET))
      .delay(5000)
      .toD("${header.callbackUrl}")
      
      .removeHeaders("*")
      .process(signedResponseUrlReading)
      .setHeader(Exchange.HTTP_QUERY, simple("${header.queryParameterssignedResponseUrl}"))
      .toD("${header.urlStartingsignedResponseUrl}")
      .process(readingFileName)
      .split(new ZipSplitter()).streaming().convertBodyTo(String.class)
      .choice()
          .when(body().isNotNull())
          .choice()
          .when(PredicateBuilder.or(body().contains("<manifest"),body().contains("<assessmentTest")))
            .toD("file:{{question.folder}}extractedFiles2\\${exchangeProperty.questionContentItemId}")
          .otherwise()
            .toD("file:{{question.folder}}extractedFiles\\${exchangeProperty.questionContentItemId}")
        .end()
      .end()
      .end()
      .setHeader("questionContentItemId", simple("${exchangeProperty.questionContentItemId}", Integer.class))
      .pollEnrich().simple("file:{{question.folder}}extractedFiles\\$simple{header.questionContentItemId}?antInclude=*item.xml")
      .timeout(50)
      .convertBodyTo(String.class)
      .choice()
          .when(PredicateBuilder.or(body().contains("<extendedTextInteraction")))
              .to("direct:RmaProcessingQuestionRoute")
          .endChoice()
      .end()
      .end()
    .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE + " For Candidate Id: ${exchangeProperty.candidateId}")
    .end();
  }
}
