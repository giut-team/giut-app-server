package com.giut.server.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;
import java.time.YearMonth;

@Converter
public class YearMonthAttributeConverter implements AttributeConverter<YearMonth, LocalDate> {

    @Override
    public LocalDate convertToDatabaseColumn(YearMonth attribute) {
        return attribute == null ? null : attribute.atDay(1);
    }

    @Override
    public YearMonth convertToEntityAttribute(LocalDate databaseValue) {
        return databaseValue == null ? null : YearMonth.from(databaseValue);
    }
}
