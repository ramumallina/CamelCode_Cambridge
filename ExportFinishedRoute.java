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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.GenerateUuid;
import com.ca.ceil.marking.svc.camelprocessor.ReadingPresignedUrl;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import com.ca.ceil.marking.svc.utility.URLConstants;

@Component
public class ExportFinishedRoute extends RouteBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExportFinishedRoute.class);

  @Value("${exception.redelivery.api.delay}")
  private int maxRedeliveryDelay;
  
  @Value("${exception.redelivery.api.attempts}")
  private int maxRedeliveries;
  
  @Autowired
  ReadingPresignedUrl readingPresignedUrl;
  
  @Autowired
  GenerateUuid generateUuid;
  
  @SuppressWarnings("unchecked")
  @Override
  public void configure() throws Exception {
    
    onException(ConnectException.class, SocketTimeoutException.class, HttpOperationFailedException.class, HttpException.class)
      .maximumRedeliveries(maxRedeliveries)
      .redeliveryDelay(maxRedeliveryDelay)
      .handled(true)
      .log(LoggingLevel.ERROR,LOGGER,"Http Connection Error:::"+ CEILConstants.LOG_ERROR_MESSAGE + " for associatedObjectId: ${header.associatedObjectId}")
    .end();
    
    onException(Exception.class)
      .handled(true)
      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE + " for candidateId: ${header.associatedObjectId}")
    .end();

    from(CEILConstants.DIRECT_SUBMISSION_EXPORT_FINISHED)
        .routeId(getClass().getName())
        .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " for associatedObjectId: ${header.associatedObjectId}")
        .setProperty(CEILConstants.SUBMISSION_ASSOCIATED_OBJECT_ID,simple("${header.associatedObjectId}"))
        .log(LoggingLevel.INFO,LOGGER,"SubmissionExport Finished WebHookNotification for associatedObjectId: ${exchangeProperty.associatedObjectId} is ${body}")
        .setProperty(CEILConstants.SUBMISSION_EXPORT_FINISHED_JSON,simple("${body}"))
        .setProperty(CEILConstants.ALTR_AGGREGATION, simple(null))
        .convertBodyTo(String.class)
        .setHeader(Exchange.CONTENT_TYPE, simple(CEILConstants.TOKEN_CONTENT_TYPE))
        .setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_POST))
        .to(URLConstants.TOKEN_ENDPOINT_URL)
        .unmarshal().json(JsonLibrary.Jackson)
        .setHeader(CEILConstants.AUTHORIZATION_KEY, simple("Bearer ${body['access_token']}"))
        .setProperty(CEILConstants.AUTHORIZATION_KEY,simple("${header.Authorization}"))
        
        .removeHeaders("*")
        .process(readingPresignedUrl)
        .setHeader(Exchange.HTTP_QUERY, simple("${header.queryParameters}"))
        .toD("${header.urlStarting}")
        .log(LoggingLevel.INFO,LOGGER,"Candidate Response From Inspera System for candidateId: ${exchangeProperty.associatedObjectId} is:::${body}")
        
        .setHeader(CEILConstants.CANDIDATE_BP_ID,jsonpath("$.ext_inspera_candidates[0].result.student.sourcedId"))
        .setHeader(CEILConstants.CANDIDATE_ID, jsonpath("$.ext_inspera_candidates[0].result.student.sourcedId"))
        
        .setHeader(CEILConstants.TEST_ID, jsonpath("$.lineItemSourcedId"))
        .setHeader(CEILConstants.TEST_TITLE, jsonpath("$.ext_inspera_assessmentRunTitle"))     
        
        .setProperty(CEILConstants.CANDIDATE_ID, jsonpath("$.ext_inspera_candidates[0].result.student.sourcedId"))
        .setProperty(CEILConstants.CANDIDATE_RESPONSE, simple("${body}"))
        .to(CEILConstants.DIRECT_PRODUCT_LOOKUP_TABLE)
        .setHeader(CEILConstants.POS_ID, simple("${exchangeProperty.posId}").convertToString())
        .setBody(simple("${exchangeProperty.candidateResponse}"))
        .split().jsonpath("$.ext_inspera_candidates[0].result.ext_inspera_questions")
          .setHeader(CEILConstants.INSPERA_QUESTION_TYPE, jsonpath("$.ext_inspera_question_type"))
          .choice()
           .when(header(CEILConstants.INSPERA_QUESTION_TYPE).isEqualTo(CEILConstants.LONG_WRITTEN_ANSWER_IDENTIFIER))
             .choice()
               .when(header(CEILConstants.FOR_ELIT).isEqualTo("yes"))
                 .bean(generateUuid,"generateAnswerUUID")
               .otherwise()
                 .setHeader(CEILConstants.ANSWER_UUID,simple(null))
             .end()  
             .setHeader(CEILConstants.QUESTION_ID, jsonpath("$.ext_inspera_questionId"))
             .setHeader(CEILConstants.RMA_HTML_FILE_IDENTIFIER, simple("${header.candidateId}"+"${header.questionId}" ))
             .setHeader(CEILConstants.QUESTION_CONTENT_ITEM_ID, jsonpath("$.ext_inspera_questionContentItemId"))
             .setHeader(CEILConstants.QUESTION_TITLE, jsonpath("$.ext_inspera_questionTitle"))
             .setHeader("duration", jsonpath("$.ext_inspera_durationSeconds"))
             .setHeader(CEILConstants.CANDIDATE_ANSWER,jsonpath("$.ext_inspera_candidateResponses[0].ext_inspera_response"))
             .setHeader(CEILConstants.CREATE_DATE, simple("${date:now:yyyy-MM-dd HH:mm:ss.SSS}"))
             .to(CEILConstants.SQL_QUERY_INSERTION_ITEM_WISE)
             .log(LoggingLevel.INFO,LOGGER,"Item wise data inserted successfully for questionId: ${header.questionId} for candidateId: ${exchangeProperty.associatedObjectId}")
          .end()
         .end()
         .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE + " for candidateId: ${exchangeProperty.associatedObjectId}")
         .choice()
          .when(exchangeProperty(CEILConstants.CEIL_MARKING_MODE).contains(CEILConstants.HYBRID_MARKING_MODE))
            .to(CEILConstants.DIRECT_ELIT_MARKING_SYSTEM)
          .when(exchangeProperty(CEILConstants.CEIL_MARKING_MODE).contains(CEILConstants.EXAMINER_MARKING_MODE))
            .to(CEILConstants.DIRECT_RMA_MARKING_SYSTEM)
          .when(PredicateBuilder.or(exchangeProperty(CEILConstants.CEIL_MARKING_MODE).contains(CEILConstants.TRAINING_MARKING_MODE),exchangeProperty(CEILConstants.CEIL_MARKING_MODE).contains(CEILConstants.PILOT_MARKING_MODE)))
            .multicast().parallelProcessing()
            .to(CEILConstants.DIRECT_RMA_MARKING_SYSTEM , CEILConstants.DIRECT_ELIT_MARKING_SYSTEM)
        .end();
  }
}