package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.ElitPrimingMetaDataReading;
import com.ca.ceil.marking.svc.camelprocessor.GenerateMediaPayloadProcessor;
import com.ca.ceil.marking.svc.camelprocessor.ReadingQuestionInformationProcessor;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class ReadingQuestionItemFileRoute extends RouteBuilder{

  private static final Logger LOGGER = LoggerFactory.getLogger(ReadingQuestionItemFileRoute.class);
  
  @Autowired
  GenerateMediaPayloadProcessor generateMediaPayload;
  
  @Autowired
  ElitPrimingMetaDataReading elitPrimingMetaDataReading;
  
  /*
   * ReadingQuestionItemFileRoute will check several if else conditions, insert data into DB and call the next route 
   * 
   * @exception Exception throws generic exception
   *
   * Control comes from: ReadingQuestionRoute Control goes to: ElitPrimingReadingRoute
   */
  
  @Override
  public void configure() throws Exception {
    
    onException(Exception.class)
      .handled(true)
      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE + " for questionContentItemId: ${exchangeProperty.contextObjectId}")
    .end();
    
    from("direct:readingQuestionItemFileRoute")
      .routeId(getClass().getName())
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " for questionContentItemId: ${exchangeProperty.contextObjectId}")
      .choice()
        .when(body().contains("<assessmentStimulusRef"))
          .setHeader("assessmentStimulusRef").xpath("/*[name()='assessmentItem']/*[name()='assessmentStimulusRef']/@href",String.class)
          .setHeader("contextObjectId", simple("${exchangeProperty.contextObjectId}", Integer.class))
          .pollEnrich().simple("file:{{elit.priming.question.folder}}/extractedFiles/$simple{header.contextObjectId}/.camel?fileName=$simple{header.assessmentStimulusRef}")
          .timeout(5000)
          .log("data after poll enrich::: ${body}, headers ::: ${headers}, elit primining folder name {{elit.priming.question.folder}}")
          .bean(ReadingQuestionInformationProcessor.class,"readQuestionDescription")
          .process(elitPrimingMetaDataReading)
          .setHeader("elitQuestionId", simple("${exchangeProperty.elitQuestionId}", Integer.class))
          .setHeader("currentValue", constant("live"))
          .setHeader("previousValue", constant("draft"))
          .setHeader("contextObjectId", simple("${exchangeProperty.contextObjectId}", Integer.class))
          .setHeader("questionDescription", simple("${exchangeProperty.questionDescription}"))
          .setHeader("creationDate", simple("${date:now:yyyy-MM-dd HH:mm:ss.SSS}"))
          .setHeader("uuidStatus", constant("ready"))
          .setHeader("uuid", simple("${exchangeProperty.uuid}"))
          .setHeader("elitMetaData", simple("${exchangeProperty.elitMetaData}"))
          .setHeader("revisionId", simple("${exchangeProperty.revisionId}", Integer.class))
          
          .choice()
            .when(exchangeProperty("imageList").isNotNull())
              .process(generateMediaPayload)
              .setHeader("imagePath", simple("${exchangeProperty.imagepathList}",String.class))
              .setHeader("mediaUuid", simple("${exchangeProperty.mediaUuidList}",String.class))
            .otherwise()
              .setHeader("imagePath", constant(null))
              .setHeader("mediaUuid", constant(null))
            .end()
            
          .to(CEILConstants.ELIT_PRIMING_INSERT_QUERY_URL)
          .log(LoggingLevel.INFO,LOGGER,"Data Inserted for questionContentItemId: ${exchangeProperty.contextObjectId} and questionId: ${exchangeProperty.elitQuestionId}")
          .to("direct:ElitPrimingRoute")
        .endChoice()
        .otherwise()
          .setProperty("dataLibsItemtype").xpath("/*[name()='assessmentItem']/*[name()='itemBody']/*[name()='div']/*[name()='div']/*[name()='extendedTextInteraction']/@data-libs-itemtype",String.class)
          .bean(ReadingQuestionInformationProcessor.class,"readQuestionDescriptionProcess")
          .process(elitPrimingMetaDataReading)
          .setHeader("elitQuestionId", simple("${exchangeProperty.elitQuestionId}", Integer.class))
          .setHeader("currentValue", constant("live"))
          .setHeader("previousValue", constant("draft"))
          .setHeader("contextObjectId", simple("${exchangeProperty.contextObjectId}", Integer.class))
          .setHeader("questionDescription", simple("${exchangeProperty.questionDescription}"))
          .setHeader("creationDate", simple("${date:now:yyyy-MM-dd HH:mm:ss.SSS}"))
          .setHeader("uuidStatus", constant("ready"))
          .setHeader("uuid", simple("${exchangeProperty.uuid}"))
          .setHeader("elitMetaData", simple("${exchangeProperty.elitMetaData}"))
          .setHeader("revisionId", simple("${exchangeProperty.revisionId}", Integer.class))
          
          .choice()
            .when(exchangeProperty("imageList").isNotNull())
              .process(generateMediaPayload)
              .setHeader("imagePath", simple("${exchangeProperty.imagepathList}"))
              .setHeader("mediaUuid", simple("${exchangeProperty.mediaUuidList}"))
            .otherwise()
              .setHeader("imagePath", constant(null))
              .setHeader("mediaUuid", constant(null))
            .end()
            
          .to(CEILConstants.ELIT_PRIMING_INSERT_QUERY_URL)
          .log(LoggingLevel.INFO,LOGGER,"Data Inserted for questionContentItemId: ${exchangeProperty.contextObjectId} and questionId: ${exchangeProperty.elitQuestionId}")
          .to("direct:ElitPrimingRoute")
        .endChoice()
      .end()
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE  + " for questionContentItemId: ${exchangeProperty.contextObjectId}")
  .end();
  }
}