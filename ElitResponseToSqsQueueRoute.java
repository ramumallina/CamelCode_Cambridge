package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.DIRECT_RMA_PROCESSING;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_ACCEPTABLE_CONFIDENCE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_ANSWER_UUID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_API_DATABASE_ROUTE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_CONFIDENCE_SCORE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_GRADER_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_HMF_FLAG;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_HYBRID_MARKING_FLOOR;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_OVERALL;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_PAYLOAD_JSON;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_SELECT_HMF_FLAG_QUERY_URL;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_SELECT_QUERY_URL;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_AFTER_SPLIT;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ca.ceil.marking.svc.camelprocessor.CandidateLongQuestionProcessor;
import com.ca.ceil.marking.svc.utility.CEILConstants;
@Component
public class ElitResponseToSqsQueueRoute extends RouteBuilder{
  
  public static final Logger LOGGER = LoggerFactory.getLogger(ElitResponseToSqsQueueRoute.class);
  
  @Autowired
  CandidateLongQuestionProcessor candidateLongQuestionProcessor;
  
  @Value("${queue_url}")
  private String queueUrl;

  @Override
  public void configure() throws Exception {
    
    onException(Exception.class)
    .handled(true)
    .log(LoggingLevel.ERROR, LOGGER,"for answerUuid: ${header.answer_uuid} "+ CEILConstants.LOG_ERROR_MESSAGE)
    .end();
    
   from(queueUrl)
    .log(LoggingLevel.INFO, LOGGER, "Response from sqs queue is:::${body}")
    .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " for answerUuid: ${header.answer_uuid}")
    .routeId(getClass().getName())
    .setProperty(ELIT_RESPONSE_PAYLOAD_JSON,simple("${body}"))
    .split().jsonpath("$.notification.results.parts[0].answers")
    .marshal().json(JsonLibrary.Jackson)
    .log(LoggingLevel.INFO, LOGGER, "Body After Split is:::${body}")
    .setProperty(ELIT_RESPONSE_AFTER_SPLIT, simple("${body}"))
    .setHeader(ELIT_RESPONSE_CONFIDENCE_SCORE, jsonpath("$.grader-results[0].results.confidence"))
    .setHeader(ELIT_RESPONSE_OVERALL, jsonpath("$.grader-results[0].results.overall"))
    .setHeader(ELIT_RESPONSE_GRADER_ID, jsonpath("$.grader-results[0].grader-id"))
    .setHeader(ELIT_RESPONSE_ANSWER_UUID, jsonpath("$.id"))
    .wireTap(ELIT_RESPONSE_API_DATABASE_ROUTE)
    .to(ELIT_RESPONSE_SELECT_QUERY_URL)
    .log(LoggingLevel.INFO, LOGGER, "Body After select Query is:::${body}")
    .choice() 
    .when(PredicateBuilder.or(body().isEqualTo(""),body().isNull()))
        .throwException(new NullPointerException("Empty Payload Getting From Elit_Marks Table"))
    .otherwise()
    .setHeader("ceilMarkingMode", simple("${body[0][ceil_marking_mode]}"))
    .log(LoggingLevel.INFO, LOGGER, "conf score is:::${header.confidenceScore}")
    .log(LoggingLevel.INFO, LOGGER, "accept conf is::${body[0][acceptable_confidence]}")
    .setProperty(ELIT_RESPONSE_ACCEPTABLE_CONFIDENCE, simple("${body[0][acceptable_confidence]}").convertTo(Double.class))
    .setProperty(ELIT_RESPONSE_HYBRID_MARKING_FLOOR, simple("${body[0][hybrid_marking_floor]}"))
    .setProperty(CEILConstants.CANDIDATE_ID, simple("${body[0][candidate_inspera_id]}"))
    .to(ELIT_RESPONSE_SELECT_HMF_FLAG_QUERY_URL)
    .log("Body after select Query hmf is::${body}")
    .log("Header is::${in.headers}")
    .choice() 
      .when(simple("${header.CamelSqlRowCount} == 0"))
        .setProperty(ELIT_RESPONSE_HMF_FLAG, simple("false"))
      .endChoice()
      .otherwise()
        .setProperty(ELIT_RESPONSE_HMF_FLAG, simple("${body[0][hmf_flag]}"))
      .endChoice()
     .end()
     .log(LoggingLevel.INFO, LOGGER, "ELIT_RESPONSE_HMF_FLAG prop is:::${exchangeProperty.hmfFlag}")
    .choice()
        .when().simple("${header.ceilMarkingMode} == 'Hybrid marking'")
          .choice()
            .when(simple("${header.confidenceScore} > ${exchangeProperty.acceptableConfidence}"))
              .choice()
                .when().simple("${exchangeProperty.hmfFlag} == 'true'")
                  .process(candidateLongQuestionProcessor)
                  .split().body()
                  .log(LoggingLevel.INFO, LOGGER, "Calling Rma Endpoint::::${body}")
                  .to(DIRECT_RMA_PROCESSING)
                  .toD(CEILConstants.ELIT_RMA_CONFIDENCESCORE_UPDATE_QUERY_URL)
                 .endChoice()
                .otherwise()
                  .setBody(simple("${exchangeProperty.elitResponseAfterSplit}"))
                  .convertBodyTo(String.class)
                  .log(LoggingLevel.INFO, LOGGER, "calling Inspera Endpoint::::${body}")
                  .to("{{elit_response_queue_url}}")
               .endChoice()
        .otherwise()
           	.log(LoggingLevel.INFO, LOGGER, "otherwise Block is calling and calling Rma Endpoint::::${body}")
            .process(candidateLongQuestionProcessor)
            .split().body()
            .to(DIRECT_RMA_PROCESSING)
         .end()
     .end()
     .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE  + " for answerUuid: ${header.answer_uuid}")
     .end();
  }
}
