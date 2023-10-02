package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jsonvalidator.JsonValidationException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class RevisionMessageRoute extends RouteBuilder{
  
  private Logger logger = LoggerFactory.getLogger(RevisionMessageRoute.class);

  @Override
  public void configure() throws Exception {
    
	  onException(Exception.class)
	      .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
	      .log(LoggingLevel.ERROR, logger, "General Error Has Occurred ,Exception Message:: ${exception.message}")
	      .handled(true);
    
    onException(JsonValidationException.class)
	      .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
	      .handled(true)
	      .log(CEILConstants.EXCEPTION_LOG);
    
    from("direct:revisionStatusNotification").routeId("RevisionMessageRoute")
    .log("Received Revision Notification Request:::${body}")
    .setHeader("event",jsonpath("$.event"))
    .choice()
       .when(header("event").isEqualTo("revision_status_updated"))
           .to("json-validator:RevisionStatusNotificationJsonSchema.json")
           .log("Body After json validation is:::${body}")
           .setProperty("revisionStatusPayload", simple("${body}"))
           .unmarshal().json(JsonLibrary.Jackson)
           .log("Body After unmarshal is:::${body}")
           .setProperty("contextObjectId",jsonpath("$.contextObjectId"))
           .setProperty("current_value",jsonpath("$.extraInfo.value"))
           .choice()
               .when(simple("${exchangeProperty.current_value}").contains("live"))
               .setBody(simple("${exchangeProperty.revisionStatusPayload}"))
               .log("Body After settingProperty is:::${body}")
                   .wireTap("direct:revisionStatusNotificationRoute")
                   .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
               .endChoice()
               .otherwise()
                   .log("value field not containing live value")
                   .setHeader("flag", constant(true))
                   .bean(ResponseMessageDto.class,"setResponseMessage(${exchange}, ${exception})")
            .end()
       .endChoice()
       .when(header("event").isEqualTo("verification"))
         .bean(ResponseMessageDto.class,"setResponseMessage(${exchange}, ${exception})")
       .endChoice()
       .otherwise()
         .log("Invalid Request Body")
         .setHeader("flag", constant(true))
         .bean(ResponseMessageDto.class,"setResponseMessage(${exchange}, ${exception})")
   .end();
  }
}
