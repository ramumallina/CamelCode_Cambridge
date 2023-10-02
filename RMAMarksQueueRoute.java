package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.RMA_CANDIDATE_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.RMA_MARKS_SELECT_QUERY;
import static com.ca.ceil.marking.svc.utility.CEILConstants.RMA_RESPONSE_PAYLOAD;
import static com.ca.ceil.marking.svc.utility.CEILConstants.RMA_TASK_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.RMA_TEST_ID;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ca.ceil.marking.svc.camelprocessor.FetchMarkMappingData;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.model.RmaMarksProcessor;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import com.ca.ceil.marking.svc.utility.RandomIdentifier;

@Component
public class RMAMarksQueueRoute extends RouteBuilder {

	public static final Logger LOGGER = LoggerFactory.getLogger(RMAMarksQueueRoute.class);

	@Autowired
	RmaMarksProcessor rmaMarksProcessor;

	@Value("${inspera_mark_queue_url}")
	private String insperaMarkQueueEndpoint;

	@Value("${xslt.rmaMarkEndpoint}")
	private String rmaMarkXsltEndpoint;
	 
	@SuppressWarnings("unchecked")
	@Override
	public void configure() throws Exception {

		onException(NullPointerException.class,Exception.class)
				.bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
				.log(LoggingLevel.ERROR, LOGGER," Error occured For Candidate Id::: ${header.candidateId} "+ CEILConstants.LOG_ERROR_MESSAGE)
				.handled(true);

		from("{{queue_url_rma}}")
				.setProperty("readingXmlFromJson",jsonpath("$.Message"))
				.setBody(simple("${exchangeProperty.readingXmlFromJson}"))
	 			.setHeader("candidateId", xpath("*[name()='GetItemMarksReturnResponse']/*[name()='GetItemMarksReturnResult']/*[name()='itemArray']/*[name()='ItemMarksReturnData']/*[name()='ArrayOfItemMarksReturnData']/*[name()='Candidate']/*[name()='UCI']/*[name()='Id']/text()",String.class))
				.log(LoggingLevel.INFO, LOGGER, CEILConstants.LOG_ROUTE_MESSAGE_START + " For Candidate Id ${header.candidateId}")		
				.routeId(getClass().getName())
				.log(LoggingLevel.INFO, LOGGER, "Body After consuming data from SQS Queue is:::${body}")
				.setHeader(RMA_TEST_ID, xpath("//*[name()='GetItemMarksReturnResponse']/*[name()='GetItemMarksReturnResult']/*[name()='itemArray']/*[name()='ItemMarksReturnData']/*[name()='ArrayOfItemMarksReturnData']/*[name()='question_paper_id']/text()",Integer.class))
				.setHeader(RMA_CANDIDATE_ID, xpath("//*[name()='GetItemMarksReturnResponse']/*[name()='GetItemMarksReturnResult']/*[name()='itemArray']/*[name()='ItemMarksReturnData']/*[name()='ArrayOfItemMarksReturnData']/*[name()='Candidate']/*[name()='UCI']/*[name()='Id']/text()",Integer.class))
				.setProperty(RMA_RESPONSE_PAYLOAD,simple("${body}"))
				.to(RMA_MARKS_SELECT_QUERY)
				.bean(FetchMarkMappingData.class,"fetchRmaData")
				
				.setBody(simple("<root>${exchangeProperty.rmaResponsePayload}</root>"))
				.to(rmaMarkXsltEndpoint) 
				.log(LoggingLevel.INFO, LOGGER,"RMA Mark payload :::${body}")
		     	.to(insperaMarkQueueEndpoint)
				.log(LoggingLevel.INFO, LOGGER, CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE + " For Candidate Id ${header.candidateId}");

	}
			
}
