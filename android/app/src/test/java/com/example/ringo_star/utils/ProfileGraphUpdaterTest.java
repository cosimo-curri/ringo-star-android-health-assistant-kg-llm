package com.example.ringo_star.utils;

import com.example.ringo_star.data.entity.User;
import com.example.ringo_star.enumeration.BloodGroup;
import com.example.ringo_star.enumeration.Gender;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.util.Values;
import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.*;

public class ProfileGraphUpdaterTest {
    private static User profile(String first, int weight, boolean smoke) {
        User user = new User();
        user.setFirstname(first);
        user.setLastname("Example");
        user.setBirthday(LocalDate.of(1990, 1, 1));
        user.setGender(Gender.MALE);
        user.setHeight(180);
        user.setBloodGroup(BloodGroup.zero_positive);
        user.setWeight(weight);
        user.setSmoke(smoke);
        return user;
    }

    @Test public void updatesProfileWithoutDroppingQuestionnaireHistory() {
        LocalDate date = LocalDate.of(2024, 12, 6);
        Model first = ProfileGraphUpdater.apply(null, profile("A", 70, false), date);
        IRI questionnaire = Values.iri(RingoStarRDF4J.nodeNS, "questionnaire-example");
        first.add(RingoStarRDF4J.nodeUserIRI, RingoStarRDF4J.relationCompile, questionnaire);
        first.add(questionnaire, RingoStarRDF4J.propertyName, Values.literal("FINDRISC"));
        Model second = ProfileGraphUpdater.apply(first, profile("B", 75, true), date);
        assertTrue(second.contains(RingoStarRDF4J.nodeUserIRI, RingoStarRDF4J.relationCompile, questionnaire));
        assertEquals("B", RingoStarRDF4J.getLiteralValue(second, RingoStarRDF4J.nodeUserIRI,
                RingoStarRDF4J.propertyFirstnameIRI).orElse(""));
        assertEquals(4, second.filter(RingoStarRDF4J.nodeUserIRI,
                RingoStarRDF4J.relationCarry, null).size());
        assertEquals(2, first.filter(RingoStarRDF4J.nodeUserIRI,
                RingoStarRDF4J.relationCarry, null).size());
        Model repeated = ProfileGraphUpdater.apply(second, profile("B", 75, true), date);
        assertEquals(second, repeated);
    }
}
