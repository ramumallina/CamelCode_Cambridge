package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.support.processor.validation.SchemaValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXParseException;
import com.ca.ceil.marking.svc.model.ProductLookupStructure;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class UpdateProductRoute extends RouteBuilder {

  public static final Logger LOGGER = LoggerFactory.getLogger(UpdateProductRoute.class);

  @SuppressWarnings("unchecked")
  @Override
  public void configure() throws Exception {

    onException(Exception.class, NullPointerException.class)
      .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
      .handled(true)
      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE+ " For PosId: ${header.posId}")
    .end();
    
    onException(SchemaValidationException.class, SAXParseException.class, ValidationException.class)
      .log(LoggingLevel.ERROR,LOGGER,"Validation Error:::" + CEILConstants.LOG_ERROR_MESSAGE+ " For PosId: ${header.posId}")
      .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
      .handled(true)
    .end();
    
    from(CEILConstants.DIRECT_UPDATE_PRODUCT_ROUTE)
      .routeId(getClass().getName())
      
      .to("json-validator:UpdationJsonSchemaValidator.json")
      .unmarshal().json(JsonLibrary.Jackson, ProductLookupStructure.class)
      .setHeader(CEILConstants.POS_ID, simple("${body.programmeOfStudyIdentifier}"))
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " For PosId: ${header.posId}")
      .setHeader(CEILConstants.PLT_PRODUCT_NAME, simple("${body.productName}"))
      .setHeader(CEILConstants.PLT_COMPONENT, simple("${body.component}"))
      .setHeader(CEILConstants.PLT_CEIL_MARKING_MODE, simple("${body.ceilMarkingMode}"))
      .setHeader(CEILConstants.PLT_HYBRID_MARKING_FLOOR, simple("${body.hybridMarkingFloor}"))
      .setHeader(CEILConstants.PLT_ACCEPTABLE_CONFIDENCE, simple("${body.acceptableConfidence}"))
      .setHeader(CEILConstants.PLT_TEMPLATE_IDENTIFIER, simple("${body.elitAutomarkingTemplateIdentifier}"))
      .setHeader(CEILConstants.PLT_TEMPLATE_VERSION, simple("${body.elitAutomarkingTemplateVersion}"))
      .toD(CEILConstants.UPDATE_PRODUCT_CRUD_CHECK_QUERY)
      .setHeader("count",simple("${body[0][count]}"))
      .choice() 
        .when(PredicateBuilder.or(simple("${header.posId}").isEqualTo(""),simple("${header.posId}").isNull()))
          .throwException(new NullPointerException("Programme Of Study Identifier is mandatory for Updating"))
        .when(simple("${header.count}").isEqualTo("0"))
          .log(LoggingLevel.INFO,LOGGER,"The product is in 'active' status or product is not in the table. Cannot perform Updation.")
          .throwException(new NullPointerException("The product is in 'active' status or product is not in the table. Cannot perform Updation."))
        .otherwise()
          .toD(CEILConstants.UPDATE_PRODUCT_CRUD_QUERY)
          .log(LoggingLevel.INFO,LOGGER,"Updation into the Database is successful")
          .setHeader("successMessage", simple("Product is updated in the Database"))
          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
          .setBody(simple(CEILConstants.SUCCESS_ACKNOWLEDGEMENT_CRUD))
    .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE+ " For PosId: ${header.posId}")
  .end();
  }
}