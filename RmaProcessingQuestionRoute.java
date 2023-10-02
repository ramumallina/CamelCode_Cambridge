package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.HtmlProcessor;
import com.ca.ceil.marking.svc.camelprocessor.ReadingFolderName;
import com.ca.ceil.marking.svc.camelprocessor.ReadingQuestionInformationProcessor;
import com.ca.ceil.marking.svc.camelprocessor.ReadingQuestionInformationProcessorRma;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class RmaProcessingQuestionRoute extends RouteBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(RmaProcessingQuestionRoute.class);
  
  @Autowired
  ReadingFolderName readingFolderName;
  
  @SuppressWarnings("unchecked")
  @Override
  public void configure() throws Exception {
    
    onException(NullPointerException.class, Exception.class)
      .handled(true)
      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE+ " For Candidate Id: ${exchangeProperty.candidateId}")
    .end();
    
    from("direct:RmaProcessingQuestionRoute")
    .routeId(getClass().getName())
    .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " For Candidate Id: ${exchangeProperty.candidateId}")
    .choice()
        .when(body().contains("<assessmentStimulusRef"))
          .setHeader("assessmentStimulusRef").xpath("/*[name()='assessmentItem']/*[name()='assessmentStimulusRef']/@href",String.class)
          .setHeader("questionContentItemId", simple("${exchangeProperty.questionContentItemId}", Integer.class))
          .pollEnrich().simple("file:{{question.folder}}extractedFiles\\$simple{header.questionContentItemId}?fileName=$simple{header.assessmentStimulusRef}")
          .timeout(50)
          .bean(ReadingQuestionInformationProcessorRma.class,"readQuestionDescription")
          .bean(HtmlProcessor.class,"HtmlGeneratorProcessor")
          .choice()
            .when(exchangeProperty("imageList").isNotNull())
              .bean(HtmlProcessor.class,"ImageUpload")
            .end()
          .to("direct:rmaRequestPayload")
      .endChoice()
      .otherwise()
        .setProperty("dataLibsItemtype").xpath("/*[name()='assessmentItem']/*[name()='itemBody']/*[name()='div']/*[name()='div']/*[name()='extendedTextInteraction']/@data-libs-itemtype",String.class)
        .bean(ReadingQuestionInformationProcessorRma.class,"readQuestionDescriptionProcess")
        .bean(HtmlProcessor.class,"HtmlGeneratorProcessor")
        .choice()
          .when(exchangeProperty("imageList").isNotNull())
            .bean(HtmlProcessor.class,"ImageUpload")
          .end()
        .to("direct:rmaRequestPayload")
      .endChoice()
    .end()
    .end()
    .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE+ " For Candidate Id: ${exchangeProperty.candidateId}")
    .end();
  }
}