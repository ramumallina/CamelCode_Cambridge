package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.model.RmaMarksProcessor;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class RMAMarksDBRoute extends RouteBuilder {

	public static final Logger LOGGER = LoggerFactory.getLogger(RMAMarksDBRoute.class);
	
	
	@Autowired
	RmaMarksProcessor rmaMarksProcessor;

	@Override
	public void configure() throws Exception {
				
		onException(Exception.class)
	        .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
			.log(LoggingLevel.ERROR, LOGGER," Error occured For Candidate Id::: ${header.candidateId} "+ CEILConstants.LOG_ERROR_MESSAGE)
			.handled(true);
		
		from(CEILConstants.DIRECT_RMA_MARKS_DB_ROUTE)
			.setHeader("candidateId", xpath("*[name()='GetItemMarksReturnResponse']/*[name()='GetItemMarksReturnResult']/*[name()='itemArray']/*[name()='ItemMarksReturnData']/*[name()='ArrayOfItemMarksReturnData']/*[name()='Candidate']/*[name()='UCI']/*[name()='Id']/text()",String.class))
		 	.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " For Candidate Id ${header.candidateId}")
			.routeId(getClass().getName())
			.setHeader("CamelAwsSnsMessageStructure", constant("xml"))
			.wireTap("{{rma.marks.snstopic}}")
			.process(rmaMarksProcessor)
			.log("Data Inserting to DB:::${body}")
			.to(CEILConstants.RMA_INSERTION_MARKS)
			.split(simple("${exchangeProperty.schemeList}"))
			.to(CEILConstants.RMA_INSERTION_ITEM_SCHEME_MARKS)
			.log("Data Inserted Successfully")
		    .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE + " For Candidate Id ${header.candidateId}");
		
	}

}
