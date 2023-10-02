package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_API_ROUTE;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.CandidateLongQuestionProcessor;
import com.ca.ceil.marking.svc.utility.CEILConstants;
@Component
public class ElitResponseRoute extends RouteBuilder{

  public static final Logger LOGGER = LoggerFactory.getLogger(ElitResponseRoute.class);
  
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
    
    from(ELIT_RESPONSE_API_ROUTE)
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " for answerUuid: ${header.answer_uuid}")
      .routeId(getClass().getName())
      .toD(queueUrl)
      .log(LoggingLevel.INFO, LOGGER, "Body After sending to SQS Queue is:::${body}")
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE  + " for answerUuid: ${header.answer_uuid}")
      .end();
  }
}
