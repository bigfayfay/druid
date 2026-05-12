package com.ankki.druid.parser;

import com.alibaba.druid.DbType;

import java.util.ArrayList;
import java.util.List;


public class AkDruidResult {
    DbType dbType;
    AkSqlParserStatusEnum status;
    String template = "";
    String removeBindingParameterSql = "";
    String md5 = "";
    String firstOperType;
    List<String> tableName = new ArrayList<>();
    List<String> tableNameAndOperType = new ArrayList<>();
    List<String> fieldName = new ArrayList<>();
    List<String> Conditions = new ArrayList<>();
    List<Object> outParameterValue = new ArrayList<>();
    List<String> functions = new ArrayList<>();
    Boolean sqlInject;
    String exceptionMsg;

    public Boolean getSqlInject() {
        return sqlInject;
    }

    public void setSqlInject(Boolean sqlInject) {
        this.sqlInject = sqlInject;
    }

    public DbType getDbType() {
        return dbType;
    }

    public void setDbType(DbType dbType) {
        this.dbType = dbType;
    }

    public AkSqlParserStatusEnum getStatus() {
        return status;
    }

    public void setStatus(AkSqlParserStatusEnum status) {
        this.status = status;
    }

    public String getFirstOperType() {
        return firstOperType;
    }

    public void setFirstOperType(String firstOperType) {
        this.firstOperType = firstOperType;
    }

    public List<String> getTableName() {
        return tableName;
    }

    public void setTableName(List<String> tableName) {
        this.tableName = tableName;
    }

    public List<String> getTableNameAndOperType() {
        return tableNameAndOperType;
    }

    public void setTableNameAndOperType(List<String> tableNameAndOperType) {
        this.tableNameAndOperType = tableNameAndOperType;
    }

    public List<String> getFieldName() {
        return fieldName;
    }

    public void setFieldName(List<String> fieldName) {
        this.fieldName = fieldName;
    }

    public List<String> getConditions() {
        return Conditions;
    }

    public void setConditions(List<String> conditions) {
        Conditions = conditions;
    }

    public List<Object> getOutParameterValue() {
        return outParameterValue;
    }

    public void setOutParameterValue(List<Object> outParameterValue) {
        this.outParameterValue = outParameterValue;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getRemoveBindingParameterSql() {
        return removeBindingParameterSql;
    }

    public void setRemoveBindingParameterSql(String removeBindingParameterSql) {
        this.removeBindingParameterSql = removeBindingParameterSql;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public String getExceptionMsg() {
        return exceptionMsg;
    }

    public void setExceptionMsg(String exceptionMsg) {
        this.exceptionMsg = exceptionMsg;
    }

    public List<String> getFunctions() {
        return functions;
    }

    public void setFunctions(List<String> functions) {
        this.functions = functions;
    }
}
