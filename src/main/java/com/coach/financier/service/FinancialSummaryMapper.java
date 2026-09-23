package com.coach.financier.service;

import com.coach.financier.model.FinancialSummary;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Convertit le bloc financialSummary persistant en modèle utilisé par le Coach. */
public final class FinancialSummaryMapper {
    private FinancialSummaryMapper() {}

    public static FinancialSummary from(JsonNode synthesis) {
        JsonNode source = synthesis.path("financialSummary");
        JsonNode global = synthesis.path("global");
        if (!source.isObject()) {
            source = global;
        }
        return new FinancialSummary(
                intValue(source, "periodMonths"),
                dateValue(source, "periodStart"),
                dateValue(source, "periodEnd"),
                doubleValue(source, "averageMonthlyIncome"),
                doubleValue(source, "medianMonthlyIncome"),
                doubleValue(source, "averageMonthlyExpenses"),
                doubleValue(source, "fixedExpenses"),
                doubleValue(source, "variableExpenses"),
                doubleValue(source, "averageMonthlySavings"),
                doubleValue(source, "averageDisposableIncome"),
                doubleValue(source, "currentAccountBalance"),
                doubleValue(source, "savingsBalance", synthesis.path("savingsTotal").asDouble(0)),
                doubleValue(source, "monthlyLoanPayments"),
                doubleValue(source, "debtServiceToIncomeRatio"),
                doubleValue(source, "savingsToIncomeRatio"),
                intValue(source, "overdraftOccurrences"),
                doubleValue(source, "minimumObservedBalance"),
                doubleValue(source, "largestRecentExpense"),
                source.path("largestRecentExpenseLabel").asText(""),
                longValue(source, "transactionCount"),
                mapValue(source.path("averageMonthlyExpensesByCategory")),
                doubleValue(source, "savingsToIncomeRatio3Months"),
                source.path("savingsRatePeriodLabel").asText("")
        );
    }

    private static LocalDate dateValue(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? null : LocalDate.parse(value);
    }

    private static int intValue(JsonNode node, String field) { return node.path(field).asInt(0); }
    private static long longValue(JsonNode node, String field) { return node.path(field).asLong(0); }
    private static double doubleValue(JsonNode node, String field) { return node.path(field).asDouble(0); }
    private static double doubleValue(JsonNode node, String field, double fallback) {
        return node.has(field) ? node.path(field).asDouble(fallback) : fallback;
    }

    private static Map<String, Double> mapValue(JsonNode node) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                result.put(field.getKey(), field.getValue().asDouble(0));
            }
        }
        return result;
    }
}

