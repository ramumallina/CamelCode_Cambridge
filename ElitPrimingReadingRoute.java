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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.ElitQuestionInformationApiCall;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import com.ca.ceil.marking.svc.utility.URLConstants;

@Component
public class ElitPrimingReadingRoute extends RouteBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElitPrimingReadingRoute.class);

  @Value("${exception.redelivery.api.delay}")
  private int maxRedeliveryDelay;

  @Value("${exception.redelivery.api.attempts}")
  private int maxRedeliveries;

  @Value("${ceil.elit.primingToken}")
  private String ceilElitPrimingToken;

  @Value("${ceil.elit.priming.accountId}")
  private String ceilElitPrimingAccountId;

  @Autowired
  ElitQuestionInformationApiCall elitQuestionInformationApiCall;

  /*
   * ElitPrimingReadingRoute will form the Priming payloads with metadata and call the ELiT system
   * 
   * @exception Exception throws generic exception
   * 
   * @exception ConnectException, HttpOperationFailedException, HttpException used when connection
   * failed with the ELiT system
   *
   * Control comes from: ReadingQuestionItemFileRoute Control goes to: ELiT system
   */
  
  @SuppressWarnings("unchecked")
  @Override
  public void configure() throws Exception {

    onException(Exception.class, NullPointerException.class)
      .handled(true)
      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE+" For questionContentItemId: ${exchangeProperty.contextObjectId}")
    .end();
    
    onException(ConnectException.class, SocketTimeoutException.class, HttpOperationFailedException.class, HttpException.class)
      .maximumRedeliveries(maxRedeliveries)
      .redeliveryDelay(maxRedeliveryDelay)
      .handled(true)
      .log(LoggingLevel.ERROR,LOGGER,"Http Connection Error:::"+ CEILConstants.LOG_ERROR_MESSAGE + " for questionContentItemId: ${exchangeProperty.contextObjectId}")
    .end();
    
    from("direct:ElitPrimingRoute")
        .routeId(getClass().getName())
        .log(LoggingLevel.INFO, LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START+ " for questionContentItemId: ${exchangeProperty.contextObjectId}")
        .setHeader("contextObjectId",simple("${exchangeProperty.contextObjectId}", Integer.class))
        .to(CEILConstants.ELIT_PRIMING_API_SELECT_QUERY)
        .split().body()
        .process(elitQuestionInformationApiCall)

        .removeHeaders("*")
        .setHeader(Exchange.CONTENT_TYPE, simple(CEILConstants.CONTENT_TYPE))
        .setHeader(CEILConstants.ELIT_ACCOUNT_ID, simple(ceilElitPrimingAccountId))
        .setHeader(Exchange.HTTP_METHOD, simple(CEILConstants.HTTP_METHOD_PUT))
        .setHeader(CEILConstants.AUTHORIZATION_KEY, simple(ceilElitPrimingToken))
        
        .setHeader(CEILConstants.QUESTION_UUID, simple("${exchangeProperty.elitQuestionUuid}"))
        .setBody(simple("${exchangeProperty.elitPrimingPayload}"))
        .log(LoggingLevel.INFO,LOGGER,"Body Before Calling ELiT Priming API Endpoint for questionContentItemId: ${exchangeProperty.contextObjectId} is::: ${body}")
        .toD(URLConstants.ELiT_QUESTION_SYSTEM_TARGET_ENDPOINT_URL)
        .log(LoggingLevel.INFO,LOGGER,"ELiT Priming API response Status Code for questionContentItemId: ${exchangeProperty.contextObjectId} is::: ${in.header.CamelHttpResponseCode}")
        .log(LoggingLevel.INFO,LOGGER,"Body After Calling ELiT Priming API Endpoint for questionContentItemId: ${exchangeProperty.contextObjectId} is::: ${body}")
        .setProperty("responseCode",simple("${in.header.CamelHttpResponseCode}", Integer.class))
        .choice()
          .when(PredicateBuilder.or(exchangeProperty("responseCode").isEqualTo(201),exchangeProperty("responseCode").isNull(),exchangeProperty("responseCode").isEqualTo("")))
            .setHeader("status", constant("sent"))
          .endChoice()
          .otherwise()
            .setHeader("status", constant("error"))
          .endChoice()
        .end()
        .toD(CEILConstants.ELIT_PRIMING_STATUS_UPDATE_QUERY_URL)
        .log(LoggingLevel.INFO,LOGGER,"Database is updated after ELiT priming for questionContentItemId: ${exchangeProperty.contextObjectId}")
     .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE  + " for questionContentItemId: ${exchangeProperty.contextObjectId}")
     .end();
  }
}

