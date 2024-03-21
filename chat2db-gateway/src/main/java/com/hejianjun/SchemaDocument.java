package com.hejianjun;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class SchemaDocument {
    private String schema;
    private List<BigDecimal> vector;
}
