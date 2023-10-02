package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.INSPERA_MARK_RETRY_ROUTE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.MARK_PAYLOAD;
import static com.ca.ceil.marking.svc.utility.CEILConstants.RESUME_ROUTE_PATH;
import static com.ca.ceil.marking.svc.utility.CEILConstants.RETRY_SUB_ROUTE;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ca.ceil.marking.svc.utility.CEILConstants;
import com.ca.ceil.marking.svc.utility.URLConstants;

@Component
public class SendMarkToInsperaRetryRoute extends RouteBuilder {

	@Value("${exception.redelivery.api.retry.delay}")
	private int maxRedeliveryDelay;
	  
	@Value("${exception.redelivery.api.retry.attempts}")
	private int maxRedeliveries;
	
	@Value("${inspera.mark.endpoint}")
	private String insperMarkEndpoint;
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SendMarkToInsperaRetryRoute.class);

	@SuppressWarnings("unchecked")
	@Override
	public void configure() throws Exception {
		
		onException(ConnectException.class, SocketTimeoutException.class)
		  .logRetryAttempted(true)
		  .retryAttemptedLogLevel(LoggingLevel.WARN)
	      .maximumRedeliveries(maxRedeliveries)
	      .redeliveryDelay(maxRedeliveryDelay)
	      .log(LoggingLevel.INFO, LOGGER,"Retry successful for exception occurred due to HTTP Exception")
	      .handled(true)
	      .end();
		
		onException(Exception.class)
		    .handled(true)
		    .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE)
		    .end(); 
		 
		 from(INSPERA_MARK_RETRY_ROUTE)
		 	.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START)
	     	.routeId(getClass().getName())
	     	.log(LoggingLevel.INFO, LOGGER,"Sending marks to Inspera")
	     	.streamCaching()
	     	.setProperty(MARK_PAYLOAD,simple("${body}"))
	        .setHeader(Exchange.CONTENT_TYPE, simple(CEILConstants.TOKEN_CONTENT_TYPE))
	        .setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_POST))
	     	.log(LoggingLevel.INFO, LOGGER, "In Seda retry route, calling the Inspera Rest endpoint")
	     	.to(RETRY_SUB_ROUTE)
	     	.toD(RESUME_ROUTE_PATH)
	     	.end();
	     	
	     from(RETRY_SUB_ROUTE)
	     	.streamCaching()
	     	.errorHandler(noErrorHandler())
	     	.log(LoggingLevel.INFO, LOGGER,"---Retrying---")
	        .to(URLConstants.TOKEN_ENDPOINT_URL)
	        .unmarshal().json(JsonLibrary.Jackson)
	        .setHeader(CEILConstants.AUTHORIZATION_KEY, simple("Bearer ${body['access_token']}"))
	     	.setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_POST))
	        .setHeader(Exchange.CONTENT_TYPE, constant(CEILConstants.CONTENT_TYPE))
	        .setBody(simple("${exchangeProperty.markPayload}"))
	     	.toD(insperMarkEndpoint)
	     	.log(LoggingLevel.INFO, "Http response code is:: ${header.CamelHttpResponseCode} \npResponseBody: ${body} ")
	     	.log(LoggingLevel.INFO,LOGGER,LOG_ROUTE_MESSAGE_COMPLETE)
	     	.end();
		
	}
}
