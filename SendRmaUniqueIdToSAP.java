package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.SENT_TO_SAP;
import static com.ca.ceil.marking.svc.utility.CEILConstants.LOG_ERROR_MESSAGE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.RMA_SESSION_IDENTIFIER;
import static com.ca.ceil.marking.svc.utility.CEILConstants.RMA_UNIQUEID_SELECT_QUERY;
import static com.ca.ceil.marking.svc.utility.CEILConstants.RMA_UNIQUEID_UPDATE_QUERY;
import static com.ca.ceil.marking.svc.utility.CEILConstants.RMA_UNIQUE_ID_PAYLOAD;
import static com.ca.ceil.marking.svc.utility.CEILConstants.QUESTION_ITEM_GROUP_ID;
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
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class SendRmaUniqueIdToSAP extends RouteBuilder {
	
	@Value("${exception.redelivery.api.delay}")
	private int maxRedeliveryDelay;
	
	@Value("${ceil.sap.token.clientId}")
	private String sapClientId;
	
	@Value("${ceil.sap.token.clientSecret}")
	private String sapClientSecret;
	
	@Value("${ceil.sap.token.xTrnsactionId}")
	private String sapTransactionId;
	  
	@Value("${exception.redelivery.api.attempts}")
	private int maxRedeliveries;
	
	@Value("${xslt.rmaUniqueIdPayloadEndpoint}")
	private String rmaUniqueIdPayloadXsltEndpoint;
	
	@Value("${sap.endpoint}")
	private String sapEndpoint;

	private static final Logger LOGGER = LoggerFactory.getLogger(SendRmaUniqueIdToSAP.class);
	
	@SuppressWarnings("unchecked")
	@Override
	public void configure() throws Exception {
		
		onException(ConnectException.class, SocketTimeoutException.class, HttpOperationFailedException.class, HttpException.class)
		    .maximumRedeliveries(maxRedeliveries)
		    .redeliveryDelay(maxRedeliveryDelay)
		    .handled(true)
		    .log(LoggingLevel.ERROR,LOGGER,"Http Connection Error:::"+ CEILConstants.LOG_ERROR_MESSAGE)
		    .end();
		
		onException(Exception.class) 
		    .handled(true)
		    .log(LoggingLevel.ERROR, LOGGER,LOG_ERROR_MESSAGE)
		    .end(); 
		
		from(RMA_UNIQUEID_SELECT_QUERY)
	    	.routeId(getClass().getName())
	    	.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START)
	    	.choice()
	    		.when(PredicateBuilder.or(body().isEqualTo(""),body().isNull()))
	    			.throwException(new NullPointerException("No records are fetched from DB"))
	    		.otherwise()
	    			.split(body()).streaming()
	    			.setHeader(RMA_SESSION_IDENTIFIER, simple("${body.get('session_identifier')}"))
	    			.setHeader(QUESTION_ITEM_GROUP_ID, simple("${body.get('question_item_group_identifier')}"))
	    			.setBody(simple("<data>${body}</data>"))
	    			.to(rmaUniqueIdPayloadXsltEndpoint) 
	    			.setProperty(RMA_UNIQUE_ID_PAYLOAD, simple("${body}"))
	    			.setBody(simple("${exchangeProperty.rmaUniqueIdPayload}"))
	    			.log(LoggingLevel.INFO,LOGGER,"Body Before Sending To SAP is:::${body}")
	    	        .setHeader("client_id", simple(sapClientId))  
	    	        .setHeader("client_secret", simple(sapClientSecret))
	    	        .setHeader("X-TRANSACTION-ID", simple(sapTransactionId))
	    	        .setHeader(Exchange.CONTENT_TYPE, constant(CEILConstants.CONTENT_TYPE))
	    	        .setHeader(Exchange.HTTP_METHOD, constant(CEILConstants.HTTP_METHOD_POST))
	    	        .toD(sapEndpoint)
	    			.log(LoggingLevel.INFO, "Http response code is:: ${header.CamelHttpResponseCode} \nResponseBody::: ${body} ")
	    			.setHeader(SENT_TO_SAP, simple("${date:now:yyyy-MM-dd HH:mm:ss.SSS}"))
	    			.to(RMA_UNIQUEID_UPDATE_QUERY) 
	    			.log(LoggingLevel.INFO,LOGGER,"Database updated after sending RMA uniqueId Payload to SAP successfully")
	    		.end()
	    	.end()
	    	.log(LoggingLevel.INFO,LOGGER,LOG_ROUTE_MESSAGE_COMPLETE )
	    .end();
	} 

}
 