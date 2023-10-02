package com.ca.ceil.marking.svc.camelroutes;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.http.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.CandidateLongQuestionProcessor;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class RmaCandidateResponseRoute extends RouteBuilder{
  
  private static final Logger LOGGER = LoggerFactory.getLogger(RmaCandidateResponseRoute.class);
  
  @Value("${exception.redelivery.api.delay}")
  private int maxRedeliveryDelay;
  
  @Value("${exception.redelivery.api.attempts}")
  private int maxRedeliveries;

	@Autowired
	CandidateLongQuestionProcessor candidateLongQuestionProcessor;
	
  @SuppressWarnings("unchecked")
  @Override
  public void configure() throws Exception {
    
    onException(ConnectException.class, SocketTimeoutException.class, HttpOperationFailedException.class, HttpException.class)
    .logRetryAttempted(true)
    .retryAttemptedLogLevel(LoggingLevel.WARN)
    .maximumRedeliveries(maxRedeliveries)
    .redeliveryDelay(maxRedeliveryDelay)
    .handled(true)
    .log(LoggingLevel.ERROR,LOGGER,"Http Connection Error:::"+ CEILConstants.LOG_ERROR_MESSAGE)
    .end();
            
      onException(Exception.class)
      .handled(true)
      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE)
      .end(); 
    
    from(CEILConstants.DIRECT_RMA_MARKING_SYSTEM)
    .routeId(getClass().getName())
    .to(CEILConstants.RMA_PRIMING_SELECT_QUERY_URL)
    .process(candidateLongQuestionProcessor)
    .split().body()
    .to(CEILConstants.DIRECT_RMA_PROCESSING);
  }
}
