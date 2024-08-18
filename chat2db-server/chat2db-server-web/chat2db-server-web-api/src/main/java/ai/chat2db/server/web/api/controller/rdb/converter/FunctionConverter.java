package ai.chat2db.server.web.api.controller.rdb.converter;

import ai.chat2db.server.web.api.controller.rdb.request.FunctionUpdateRequest;
import ai.chat2db.spi.model.Function;
import org.mapstruct.Mapper;

/**
 * @author Juechen
 * @version : FunctionConverter.java
 */
@Mapper(componentModel = "spring")
public abstract class FunctionConverter {
    public abstract Function request2param(FunctionUpdateRequest request);

}
