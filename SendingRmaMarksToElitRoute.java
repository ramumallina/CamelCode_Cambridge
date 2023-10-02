package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.RMA_MARKS_CEIL_MARKING_MODE_SELECT_QUERY;
import static com.ca.ceil.marking.svc.utility.CEILConstants.RMA_MARKS_SUBMISSION_UUID_SELECT_QUERY;
import static com.ca.ceil.marking.svc.utility.CEILConstants.CAME_AWS_SQS_RECEIPT_HANDLE;

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

import com.ca.ceil.marking.svc.camelprocessor.FetchCeilMarkingMode;
import com.ca.ceil.marking.svc.camelprocessor.FetchSubmissionUUID;
import com.ca.ceil.marking.svc.camelprocessor.GenerateRmaMarksJsonPayload;
import com.ca.ceil.marking.svc.camelprocessor.ReadingFeedBackUuid;
import com.ca.ceil.marking.svc.camelprocessor.ReadingTotalMarksFromRma;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import static com.ca.ceil.marking.svc.utility.URLConstants.ELiT_RMA_MARKS_FEEDBACK_SYSTEM_TARGET_ENDPOINT_URL;

@Component
public class SendingRmaMarksToElitRoute extends RouteBuilder{
	
	public static final Logger LOGGER = LoggerFactory.getLogger(SendingRmaMarksToElitRoute.class);
	
	@Value("${exception.redelivery.api.delay}")
	 private int maxRedeliveryDelay;
	  
	 @Value("${exception.redelivery.api.attempts}")
	 private int maxRedeliveries;
	 
	 @Value("${ceil.elit.priming.accountId}")
	 private String ceilElitPrimingAccountId;
	 
	 @Value("${ceil.elit.primingToken}")
	 private String ceilElitPrimingToken;
	 
	@SuppressWarnings("unchecked")
	@Override
	public void configure() throws Exception {
		
		onException(Exception.class)
	        .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
			.log(LoggingLevel.ERROR, LOGGER," Error occured For Candidate Id::: ${header.candidateId} "+ CEILConstants.LOG_ERROR_MESSAGE)
			.handled(true);
		
		onException(ConnectException.class, SocketTimeoutException.class, HttpOperationFailedException.class, HttpException.class)
	      .maximumRedeliveries(maxRedeliveries)
	      .redeliveryDelay(maxRedeliveryDelay)
	      .handled(true)
	      .log(LoggingLevel.ERROR,LOGGER,"Http Connection Error:::"+ CEILConstants.LOG_ERROR_MESSAGE + " For Candidate Id ${header.candidateId}")
	    .end();
		
		from("{{rma.marks.sqssubscription.queue}}")
		.log("sqsSubscriptionQueue is:::{{rma.marks.sqssubscription.queue}}")
			.log(LoggingLevel.INFO,log," {{subscription_queue_name}} Message recieved-->${body}")
		 	.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " For Candidate Id ${header.candidateId}")
			.routeId(getClass().getName())
			.log("Headers in SendingRmaMarksToElitRoute is:::${in.headers}")
			.setProperty(CAME_AWS_SQS_RECEIPT_HANDLE, simple("${header.CamelAwsSqsReceiptHandle}"))
			
			.setProperty("rmaMarksXml",jsonpath("$.Message"))
			.setBody(simple("${exchangeProperty.rmaMarksXml}"))
			.setProperty("questionPaperId", xpath("//*[name()='GetItemMarksReturnResponse']/*[name()='GetItemMarksReturnResult']/*[name()='itemArray']/*[name()='ItemMarksReturnData']/*[name()='ArrayOfItemMarksReturnData']/*[name()='question_paper_id']/text()",Integer.class))
			.setProperty("candidateId", xpath("*[name()='GetItemMarksReturnResponse']/*[name()='GetItemMarksReturnResult']/*[name()='itemArray']/*[name()='ItemMarksReturnData']/*[name()='ArrayOfItemMarksReturnData']/*[name()='Candidate']/*[name()='UCI']/*[name()='Id']/text()",Integer.class))
			.setProperty("markerId", xpath("*[name()='GetItemMarksReturnResponse']/*[name()='GetItemMarksReturnResult']/*[name()='itemArray']/*[name()='ItemMarksReturnData']/*[name()='ArrayOfItemMarksReturnData']/*[name()='ItemGroups']/*[name()='Marker']/@id",String.class))
			.bean(ReadingTotalMarksFromRma.class,"readingRmaMarks")
			.toD(RMA_MARKS_CEIL_MARKING_MODE_SELECT_QUERY)
			.log(LoggingLevel.INFO,LOGGER,"Body After Ceil Marking Mode Select Query is:::${body}")
			.choice()
	        	.when(PredicateBuilder.or(body().isEqualTo(""),body().isNull()))
	        		.throwException(new NullPointerException("No records are there"))
	        	.endChoice()
	        	.otherwise()
	        	.bean(FetchCeilMarkingMode.class,"ceilMarkingMode")
	        	.log(LoggingLevel.INFO,LOGGER,"ceil_marking_mode property is:::${exchangeProperty.ceil_marking_mode}")
	        		.choice()
	        			.when(exchangeProperty(CEILConstants.CEIL_MARKING_MODE).contains(CEILConstants.EXAMINER_MARKING_MODE))
	        				.log("Fully examiner marked mode no need to call ELiT API")
	        			.endChoice()
	        			.otherwise()
		        			.setHeader(CEILConstants.ELIT_ACCOUNT_ID, simple(ceilElitPrimingAccountId))
		        			.log("accountid header is:::${header.accountid}")
		        			.bean(ReadingFeedBackUuid.class,"getFeedBackUuid")
		        			.toD(RMA_MARKS_SUBMISSION_UUID_SELECT_QUERY)
		        			.choice()
		    	        	.when(PredicateBuilder.or(body().isEqualTo(""),body().isNull()))
		    	        		.throwException(new NullPointerException("No records are there"))
		    	        	.endChoice()
		    	        	.otherwise()
			        			.bean(FetchSubmissionUUID.class,"getSubmissionUuid")
			        			.bean(GenerateRmaMarksJsonPayload.class,"rmaMarksJsonPayload")
			        			.log("Body Before calling ELiT API is:::${body}")
			        			.removeHeaders("*", "accountid","feedbackUuid", "elitSubmissionUuid")
			        			.setHeader(Exchange.HTTP_METHOD, simple(CEILConstants.HTTP_METHOD_PUT))
			        			.setHeader(Exchange.CONTENT_TYPE, simple(CEILConstants.CONTENT_TYPE))
			        			.setHeader(CEILConstants.AUTHORIZATION_KEY, simple(ceilElitPrimingToken))
			        			.toD(ELiT_RMA_MARKS_FEEDBACK_SYSTEM_TARGET_ENDPOINT_URL)
			        			.setHeader(CAME_AWS_SQS_RECEIPT_HANDLE, simple("${exchangeProperty.CamelAwsSqsReceiptHandle}"))
			        			.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE + " For Candidate Id ${header.candidateId}")
			        		.end()
			        	.end()
		       .end()
		       .setHeader(CAME_AWS_SQS_RECEIPT_HANDLE, simple("${exchangeProperty.CamelAwsSqsReceiptHandle}"))
		       .log("CamelAwsSqsReceiptHandle Header is:::${in.headers}")
		       .end();
	}
}
