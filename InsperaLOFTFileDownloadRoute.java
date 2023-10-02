package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.LOFT_EVENT_NOTIFICATION_ROUTE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.LOFT_OUTPUT_URLS;
import static com.ca.ceil.marking.svc.utility.CEILConstants.LOFT_SELECT_QUERY;
import static com.ca.ceil.marking.svc.utility.CEILConstants.INSPERA_SPEAKING_TEST_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.INSPERA_LISTENING_TEST_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.INSPERA_READING_TEST_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.INSPERA_WRITING_TEST_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.MODULE_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.LOFT_PAYLOAD;
import static com.ca.ceil.marking.svc.utility.CEILConstants.LOFT_FILENAME;

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

import com.ca.ceil.marking.svc.camelprocessor.FetchInsperaTestIdProcessor;
import com.ca.ceil.marking.svc.camelprocessor.GenerateUuid;
import com.ca.ceil.marking.svc.camelprocessor.ReadingSignedLoftOutputFileUrl;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class InsperaLOFTFileDownloadRoute extends RouteBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(InsperaLOFTFileDownloadRoute.class);

  @Value("${exception.redelivery.api.delay}")
  private int maxRedeliveryDelay;
  
  @Value("${exception.redelivery.api.attempts}")
  private int maxRedeliveries;
  
  @Value("${inspera.loft.endpoint}")
  private String insperLoftEndpoint;
  
  @Value("${inspera.ceqmigration.token.url}")
  private String insperaTokenEndpoint;
  
  @Autowired
  ReadingSignedLoftOutputFileUrl readingSignedLoftOutputFileUrl;
  
  @Autowired
  FetchInsperaTestIdProcessor fetchInsperaTestIdProcessor;
  
  @Autowired
  GenerateUuid generateUuid;
  
  @SuppressWarnings("unchecked")
  @Override
  public void configure() throws Exception {
    
    onException(ConnectException.class, SocketTimeoutException.class, HttpOperationFailedException.class, HttpException.class)
      .maximumRedeliveries(maxRedeliveries)
      .redeliveryDelay(maxRedeliveryDelay)
      .handled(true)
      .log(LoggingLevel.ERROR,LOGGER,"Http Connection Error:::"+ CEILConstants.LOG_ERROR_MESSAGE + " for contextObjectId: ${exchangeProperty.contextObjectId}")
    .end();
    
    onException(Exception.class)
      .handled(true)
      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE + " for contextObjectId: ${exchangeProperty.contextObjectId}")
    .end();

    from(LOFT_EVENT_NOTIFICATION_ROUTE)
        .routeId(getClass().getName())
        .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " for contextObjectId: ${exchangeProperty.contextObjectId}")
        .convertBodyTo(String.class)
        .setHeader(Exchange.CONTENT_TYPE, simple(CEILConstants.TOKEN_CONTENT_TYPE))
        .setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_POST))
        .toD(insperaTokenEndpoint)
        .removeHeaders("*")
        .setHeader("accessToken", jsonpath("$.access_token"))
        .setHeader(CEILConstants.AUTHORIZATION_KEY, simple("Bearer ${header.accessToken}"))
        .setHeader(Exchange.CONTENT_TYPE, simple(CEILConstants.TOKEN_CONTENT_TYPE))
        .setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_GET))
        .toD(insperLoftEndpoint)
        .choice()
        	.when().jsonpath("$.loftOutputUrls[0].signedLoftOutputFileUrl", true)
	        	.removeHeaders("*")
	        	.split().jsonpath("$.loftOutputUrls")
		        .setHeader(LOFT_OUTPUT_URLS,jsonpath("$.signedLoftOutputFileUrl"))
		        .setProperty("userId",jsonpath("$.userId"))
		        .process(readingSignedLoftOutputFileUrl)
		        .setHeader(Exchange.HTTP_QUERY, simple("${header.queryParameters}"))
		        .toD("${header.signedLoftOutputFileUrl}")
		        .setProperty(LOFT_PAYLOAD, simple("${body}"))
		        .setHeader("test_id",simple("${exchangeProperty.contextObjectId}",String.class))
		        .to(LOFT_SELECT_QUERY)
		        .process(fetchInsperaTestIdProcessor)
		        .choice()
			        .when(header(INSPERA_READING_TEST_ID).isEqualTo(simple("${exchangeProperty.contextObjectId}")))
	    				.setHeader(MODULE_ID, simple("_01"))
		        	.when(header(INSPERA_WRITING_TEST_ID).isEqualTo(simple("${exchangeProperty.contextObjectId}")))
        				.setHeader(MODULE_ID, simple("_02"))
		        	.when(header(INSPERA_LISTENING_TEST_ID).isEqualTo(simple("${exchangeProperty.contextObjectId}")))
        				.setHeader(MODULE_ID, simple("_03"))
        			.when(header(INSPERA_SPEAKING_TEST_ID).isEqualTo(simple("${exchangeProperty.contextObjectId}")))
        				.setHeader(MODULE_ID, simple("_04"))
		        .endChoice()
		        .end()
		        .setBody(simple("${exchangeProperty.loftPayload}"))
		        .setHeader("camelFileName", simple("RUNID_${exchangeProperty.contextObjectId}_"+"USERID_${exchangeProperty.userId}"))
		        .choice()
			        .when(PredicateBuilder.and(header("pos").isNotNull(),header(MODULE_ID).isNotNull()))
			        	.setHeader(LOFT_FILENAME,simple("${header.camelFileName}").append(simple("_${header.pos}")).append(simple("${header.moduleId}")))
			        .otherwise()
			        	.setHeader(LOFT_FILENAME,simple("${header.camelFileName}"))
			    .end()
		        .log(LoggingLevel.INFO,LOGGER,"Received LOFT File [${header.loftFileName}.json] from Inspera System for contextObjectId: ${exchangeProperty.contextObjectId} ")
		        .toD("file:{{inspera.loft.folder}}?fileName=${header.loftFileName}.json&fileExist=Ignore")
		        .endChoice()
		     .otherwise()
        		.log(LoggingLevel.ERROR,LOGGER,"signedLoftOutputFileUrl is not found for contextObjectId: ${exchangeProperty.contextObjectId}")
    			.throwException(new NullPointerException("signedLoftOutputFileUrl is not found"))
    		.end()
    	.end()  
		.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE  + " for contextObjectId: ${exchangeProperty.contextObjectId}")
		
		.end();
  }
}
