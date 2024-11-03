package com.amazon.ata.advertising.service.targeting.predicate;

import com.amazon.ata.advertising.service.exceptions.AdvertisementServiceException;
import com.amazon.ata.advertising.service.dependency.LambdaComponent;
import com.amazon.ata.advertising.service.dependency.DaggerLambdaComponent;
import com.amazon.ata.advertising.service.dependency.TargetingPredicateInjector;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * Class to convert a list of the complex type TargetingPredicate to a string and vice-versa.
 */
public class TargetingPredicateTypeConverter implements DynamoDBTypeConverter<String, List<TargetingPredicate>> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final LambdaComponent COMPONENT = DaggerLambdaComponent.create();
    private static final TargetingPredicateInjector INJECTOR = COMPONENT.getTargetingPredicateInjector();

    /**
     * Serializes the passed predicate list into a String.
     * @param predicateList - a list of TargetingPredicates that will be converted to a String value
     * @return The serialized string. "[]" in the case of an empty list.
     */
    @Override
    public String convert(List<TargetingPredicate> predicateList) {
        try {
            // Use ObjectMapper to convert the list to a JSON string
            return MAPPER.writeValueAsString(predicateList);
        } catch (IOException e) {
            throw new AdvertisementServiceException("Unable to convert the predicate list to a String.", e);
        }
    }

    @Override
    public List<TargetingPredicate> unconvert(String value) {
        try {
            // Deserialize the JSON string back into a list of TargetingPredicate objects
            List<TargetingPredicate> predicates = MAPPER.readValue(value,
                    new TypeReference<List<TargetingPredicate>>() { });

            // Use the injector to inject dependencies into each TargetingPredicate
            for (TargetingPredicate predicate : predicates) {
                INJECTOR.inject(predicate);
            }
            return predicates;
        } catch (IOException e) {
            throw new AdvertisementServiceException("Unable to convert the String value to a list of targeting " +
                    "predicates. String: " + value, e);
        }
    }
}

