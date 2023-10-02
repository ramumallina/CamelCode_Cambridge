package com.ca.ceil.marking.svc.camelroutes;

import java.util.ArrayList;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.support.processor.validation.SchemaValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXParseException;
import com.ca.ceil.marking.svc.camelprocessor.ProductLookupTableProcessor;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class DeleteProductRoute extends RouteBuilder {

  public static final Logger LOGGER = LoggerFactory.getLogger(DeleteProductRoute.class);
  
  @SuppressWarnings("unchecked")
  @Override
  public void configure() throws Exception {

    onException(Exception.class, NullPointerException.class)
      .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
      .handled(true)
      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE)
    .end();
    
    onException(SchemaValidationException.class, SAXParseException.class, ValidationException.class)
      .log(LoggingLevel.ERROR,LOGGER,"Validation Error:::"+CEILConstants.LOG_ERROR_MESSAGE)
      .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
      .handled(true)
    .end();
  
    from(CEILConstants.DIRECT_DELETE_PRODUCT_ROUTE)
      .routeId(getClass().getName())
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START)
      .to("json-validator:DeletionJsonSchemaValidator.json")
      .unmarshal().json(JsonLibrary.Jackson, ArrayList.class)
      .bean(ProductLookupTableProcessor.class,"posIdListMaker")
      .toD(CEILConstants.DELETE_PRODUCT_CRUD_CHECK_QUERY)
      .setHeader(CEILConstants.COUNT,simple("${body[0][count]}"))
      .choice()
        .when(simple("${header.count}").isEqualTo("0"))
          .log(LoggingLevel.INFO,LOGGER,"All product(s) are in 'active' status or product(s) are not in the table. Cannot perform Deletion.")
          .throwException(new NullPointerException("All product(s) are in 'active' status or product(s) are not in the table. Cannot perform Deletion."))
        .when(simple("${header.count}").isNotEqualTo(simple("${header.actualCount}")))
          .log(LoggingLevel.INFO,LOGGER,"Only ${header.count} product(s) will be deleted and rest are in 'active' status or product(s) are not available in the table")
          .setHeader("successMessage", simple("Only ${header.count} product(s) will be deleted and rest are in 'active' status or product(s) are not available in the table"))
        .otherwise()
          .log(LoggingLevel.INFO,LOGGER,"All given product(s) will be deleted")
          .setHeader("successMessage", simple("All given product(s) will be deleted"))
          .end()
          .toD(CEILConstants.DELETE_PRODUCT_CRUD_QUERY)
          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
          .setBody(simple(CEILConstants.SUCCESS_ACKNOWLEDGEMENT_CRUD))
          .log(LoggingLevel.INFO,LOGGER,"Deletion from the Database is successful")
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE)
    .end();
  }
}