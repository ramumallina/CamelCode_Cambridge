package com.ca.ceil.marking.svc.camelroutes;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.http.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.GenerateJsonPayloadProcessor;
import com.ca.ceil.marking.svc.camelprocessor.GenerateUuid;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import com.ca.ceil.marking.svc.utility.URLConstants;

@Component
public class ElitMarkingSystemRoute extends RouteBuilder {
  
private static final Logger LOGGER = LoggerFactory.getLogger(ElitMarkingSystemRoute.class);
  
  @Value("${exception.redelivery.api.delay}")
  private int maxRedeliveryDelay;
  
  @Value("${exception.redelivery.api.attempts}")
  private int maxRedeliveries;
  
  @Value("${ceil.elit.primingToken}")
  private String ceilElitPrimingToken;
  
  /*
   * ElitMarkingSystemRoute will form the different payloads and call the ELiT system
   * 
   * @exception Exception throws generic exception
   * 
   * @exception ConnectException, HttpOperationFailedException, HttpException used when connection
   * failed with the ELiT system
   *
   * Control comes from: ExportFinishedRoute Control goes to: ELiT system
   */
  
  @SuppressWarnings("unchecked")
  @Override
  public void configure() throws Exception {
    
    onException(ConnectException.class, SocketTimeoutException.class, HttpOperationFailedException.class, HttpException.class)
      .maximumRedeliveries(maxRedeliveries)
      .redeliveryDelay(maxRedeliveryDelay)
      .handled(true)
      .log(LoggingLevel.ERROR,LOGGER,"Http Connection Error:::"+ CEILConstants.LOG_ERROR_MESSAGE+ " For Candidate Id: ${exchangeProperty.candidateId}")
    .end();
    
    onException(NullPointerException.class, Exception.class)
      .handled(true)
      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE+ " For Candidate Id: ${exchangeProperty.candidateId}")
    .end();
    
    from(CEILConstants.DIRECT_ELIT_MARKING_SYSTEM)
      .routeId(getClass().getName())
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " For Candidate Id: ${exchangeProperty.candidateId}")
      .toD(CEILConstants.SQL_QUERY_SELECT_ELIT_TEMPLATE_DETAILS)
      .choice()
        .when(PredicateBuilder.or(body().isEqualTo(""),body().isNull()))
          .throwException(new NullPointerException("ELiT Template Details is not present in Product Lookup Table"))
        .otherwise()
          .bean(GenerateJsonPayloadProcessor.class,"elitTemplatePayload")
          .toD(CEILConstants.SQL_QUERY_SELECT_ELIT_LANG_CODE_DETAILS)
          .choice()
            .when(PredicateBuilder.or(body().isEqualTo(""),body().isNull()))
              .throwException(new NullPointerException("Language code and Country code is not present in Country Language Code Table"))
            .otherwise()
              .bean(GenerateJsonPayloadProcessor.class,"countryLangCode")
              .toD(CEILConstants.SQL_QUERY_SELECT_ELIT_CANDIDATE_DETAILS)
              .choice()
                .when(PredicateBuilder.or(body().isEqualTo(""),body().isNull()))
                  .throwException(new NullPointerException("Candidate Info is not present in Candidate Details Table"))
                .otherwise()
                  .bean(GenerateJsonPayloadProcessor.class,"candidateDetailsPayload")
                  .toD(CEILConstants.SQL_QUERY_SELECT_ELIT_MARKING)
                  .choice()
                    .when(PredicateBuilder.or(body().isEqualTo(""),body().isNull()))
                      .throwException(new NullPointerException("Priming is not done for the question(s)"))
                    .otherwise()
                      .removeHeaders("*")  
                      .bean(GenerateUuid.class,"generateSubmissionUUID")
                      .bean(GenerateJsonPayloadProcessor.class,"elitSubmissionPayload")
                      
                      .setHeader(CEILConstants.CANDIDATE_ID, simple("${exchangeProperty.candidateId}"))
                      .setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_PUT))
                      .setHeader(CEILConstants.AUTHORIZATION_KEY, simple(ceilElitPrimingToken))
                      .setHeader(Exchange.CONTENT_TYPE, constant(CEILConstants.CONTENT_TYPE))
                      .log(LoggingLevel.INFO,LOGGER,"Body Before Sending To ELiT Submission API For Candidate Id: ${exchangeProperty.candidateId} is:::${body}")
                      .choice()
                        .when(exchangeProperty("ifPilot").isEqualTo(true))
                          .toD(URLConstants.ELiT_PILOT_SUBMISSION_SYSTEM_TARGET_ENDPOINT_URL)
                        .endChoice()
                        .otherwise()
                          .toD(URLConstants.ELiT_SUBMISSION_SYSTEM_TARGET_ENDPOINT_URL)
                        .endChoice()
                      .end()
                      .log(LoggingLevel.INFO,LOGGER,"ELiT Submission response Status Code For Candidate Id: ${exchangeProperty.candidateId} is:::${in.header.CamelHttpResponseCode}")
                      .log(LoggingLevel.INFO,LOGGER,"Body After Sending To ELiT Submission API For Candidate Id: ${exchangeProperty.candidateId} is:::${body}")
                      .toD(CEILConstants.SQL_QUERY_UPDATE_ELIT_SUBMISSION_STATUS)
                      .log(LoggingLevel.INFO,LOGGER,"Updated ELiT submission Status successfully For Candidate Id: ${exchangeProperty.candidateId}")
                    .end()
                  .end()
                .end()
              .end()
            .end()
          .end()
        .end()
      .end()            
   .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE+ " For Candidate Id: ${exchangeProperty.candidateId}")
   .end(); 
}
}
