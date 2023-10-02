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
import com.ca.ceil.marking.svc.camelprocessor.GenerateQppdQpmssdPayload;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import com.ca.ceil.marking.svc.utility.URLConstants;

@Component
public class RmaPrimingQpmssdRoute extends RouteBuilder{

private static final Logger LOGGER = LoggerFactory.getLogger(RmaPrimingQpmssdRoute.class);
  
  @Value("${exception.redelivery.api.delay}")
  private int maxRedeliveryDelay;
  
  @Value("${exception.redelivery.api.attempts}")
  private int maxRedeliveries;
  
  /*
   * RmaPrimingQpmssdRoute will form the qpmssd payload and call the RMA system. 
   * Triggering Point for Qpmssd: when qpmssd_sent_status='no'
   * 
   * Note: if qppd got success, but qpmssd is failed, which will update qpmssd_sent_status = 'error'. Then manual intervention is required
   * to re-trigger the qpmssd and to re-trigger it, first check whether qppd_sent_status='yes' or not,
   * if not then check RmaPrimingQppdRoute. If yes update qpmssd_sent_status = 'no'.
   * 
   * @exception Exception throws generic exception
   * 
   * @exception ConnectException, HttpOperationFailedException, HttpException used when connection
   * failed with the RMA system
   *
   * Control comes from: RmaPrimingQppdRoute Control goes to: RMA system
   */
  
  @SuppressWarnings("unchecked")
  @Override
  public void configure() throws Exception {
    
    onException(ConnectException.class, SocketTimeoutException.class, HttpOperationFailedException.class, HttpException.class)
      .maximumRedeliveries(maxRedeliveries)
      .redeliveryDelay(maxRedeliveryDelay)
      .handled(true)
      .setHeader("status", constant("error"))
      .toD(CEILConstants.RMA_PRIMING_QPMSSD_UPDATE_QUERY)
      .log(LoggingLevel.ERROR,LOGGER,"Http Connection Error:::"+ CEILConstants.LOG_ERROR_MESSAGE+" For TestId:${exchangeProperty.testIdentifier}")
    .end();
    
    onException(Exception.class, NullPointerException.class)
      .handled(true)
      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE+" For TestId:${exchangeProperty.testIdentifier}")
    .end(); 
  
    from(CEILConstants.DIRECT_RMA_PRIMING_QPMSSD_ROUTE)
      .routeId(getClass().getName())
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START+" For TestId:${exchangeProperty.testIdentifier}")
      .setHeader(CEILConstants.TEST_IDENTIFIER,simple("${exchangeProperty.testIdentifier}"))
      .toD(CEILConstants.RMA_PRIMING_QPMSSD_SELECT_QUERY)
      .choice()
        .when(PredicateBuilder.or(body().isEqualTo(""),body().isNull()))
          .throwException(new NullPointerException("No records are there to prime for QPMSSD"))
        .endChoice()
        .otherwise()
            .bean(GenerateQppdQpmssdPayload.class,"qpmssdPayloads")
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
                
            .log(LoggingLevel.INFO,LOGGER,"Body Before Sending To RMA System for TestId:${exchangeProperty.testIdentifier} is:::${body}")
            .toD(URLConstants.QPMSSD_LIVE_ENDPOINT_URL)
            .log(LoggingLevel.INFO,LOGGER,"RMA Save Mark Scheme Structure Status Code for TestId:${exchangeProperty.testIdentifier} is:::${in.header.CamelHttpResponseCode}")
            .log(LoggingLevel.INFO,LOGGER,"Body After Sending To RMA System for TestId:${exchangeProperty.testIdentifier} is:::${body}")
            .setProperty("responseCode",simple("${in.header.CamelHttpResponseCode}", Integer.class))
            .choice()
              .when(PredicateBuilder.or(exchangeProperty("responseCode").isEqualTo(200),exchangeProperty("responseCode").isNull(),exchangeProperty("responseCode").isEqualTo("")))
                .setHeader("status", constant("yes"))
              .endChoice()
              .otherwise()
                .setHeader("status", constant("error"))
              .endChoice()
            .end()
            .toD(CEILConstants.RMA_PRIMING_QPMSSD_UPDATE_QUERY)
            .log(LoggingLevel.INFO,LOGGER,"Database updated successfully after sending RMA Save Mark Scheme Structure payload for TestId:${exchangeProperty.testIdentifier}")
        .endChoice()
      .end()
    .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE+" For TestId:${exchangeProperty.testIdentifier}")
    .end();
  }
}