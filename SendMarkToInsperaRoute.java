package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.INSPERA_MARK_RETRY_ROUTE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.INSPERA_MARK_SUSPENSION_ROUTE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.LOG_ERROR_MESSAGE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.LOG_ROUTE_MESSAGE_START;
import static com.ca.ceil.marking.svc.utility.CEILConstants.MARK_PAYLOAD;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ROUTE_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.INSPERA_USER_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.QUESTION_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.CANDIDATE_DSMC_TEST_ID_SELECT;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ca.ceil.marking.svc.camelprocessor.FetchTestIdProcessor;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import com.ca.ceil.marking.svc.utility.URLConstants;
import static com.ca.ceil.marking.svc.utility.CEILConstants.CANDIDATE_TEST_ID_SELECT;
@Component 
public class SendMarkToInsperaRoute extends RouteBuilder {
	
	@Value("${inspera_mark_queue_url}")
	private String insperaMarkQueueEndpoint;
	 
	@Value("${exception.redelivery.api.delay}")
	private int maxRedeliveryDelay;
	  
	@Value("${exception.redelivery.api.attempts}")
	private int maxRedeliveries;
	
	@Value("${inspera.mark.endpoint}")
	private String insperMarkEndpoint;
	  
	private static final Logger LOGGER = LoggerFactory.getLogger(SendMarkToInsperaRoute.class);

	@Autowired
	FetchTestIdProcessor fetchTestIdProcessor;
	
	@SuppressWarnings("unchecked")
	@Override
	public void configure() throws Exception {
		
		 onException(ConnectException.class, SocketTimeoutException.class)
		 	.logRetryAttempted(true)
		 	.retryAttemptedLogLevel(LoggingLevel.WARN)
	      	.maximumRedeliveries(maxRedeliveries)
	      	.redeliveryDelay(maxRedeliveryDelay)
	      	.to(INSPERA_MARK_SUSPENSION_ROUTE)
	      	.log(LoggingLevel.ERROR,LOGGER,"Http Connection Error ::: "+LOG_ERROR_MESSAGE)
	      	.to(INSPERA_MARK_RETRY_ROUTE)
	      	.handled(true)
	      	.end();
		
		onException(Exception.class)
		    .handled(true)
		    .log(LoggingLevel.ERROR, LOGGER,LOG_ERROR_MESSAGE)
		    .end(); 
		 
		 from(insperaMarkQueueEndpoint)
			.routeId(ROUTE_ID)
			.log(LoggingLevel.INFO,LOGGER,LOG_ROUTE_MESSAGE_START)
	     	.log(LoggingLevel.INFO, LOGGER,"Sending marks to Inspera")
	     	.log("${body}")
	     	.setHeader(INSPERA_USER_ID,jsonpath("$.candidate.candidateIdValues.insperaUserId", Integer.class))
	     	.setHeader(QUESTION_ID,jsonpath("$.marks[0].questionId", Integer.class))
	     	.setProperty(MARK_PAYLOAD,simple("${body}"))
	     	.setHeader("markingSystem",jsonpath("$.marks[0].scores[0].scoreDetails[0].markingSystem"))
	     	.choice()
	     	.when(header("markingSystem").isEqualTo("DSMC"))
	     			.toD(CANDIDATE_DSMC_TEST_ID_SELECT)
	     			.bean(FetchTestIdProcessor.class,"fetchSpeakingTestId")
	     	.otherwise()
	     	.to(CANDIDATE_TEST_ID_SELECT)
	     	.process(fetchTestIdProcessor)
	     	.end()
	     	.setBody(simple(""))
	        .setHeader(Exchange.CONTENT_TYPE, simple(CEILConstants.TOKEN_CONTENT_TYPE))
	        .setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_POST))
	        .to(URLConstants.TOKEN_ENDPOINT_URL)
	        .unmarshal().json(JsonLibrary.Jackson)
	        .setHeader(CEILConstants.AUTHORIZATION_KEY, simple("Bearer ${body['access_token']}"))
	        .setProperty(CEILConstants.AUTHORIZATION_KEY,simple("${header.Authorization}"))
	     	.setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_POST))
	        .setHeader(Exchange.CONTENT_TYPE, constant(CEILConstants.CONTENT_TYPE))
	        .setBody(simple("${exchangeProperty.markPayload}"))
	        .toD(insperMarkEndpoint)
	     	.log(LoggingLevel.INFO, "Http response code is:: ${header.CamelHttpResponseCode} \n ResponseBody: ${body} ")
	     	.log(LoggingLevel.INFO,LOGGER,LOG_ROUTE_MESSAGE_COMPLETE)
	     	.end();
		
	}


}
