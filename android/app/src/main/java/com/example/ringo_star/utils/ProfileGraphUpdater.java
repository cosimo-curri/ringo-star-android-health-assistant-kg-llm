package com.example.ringo_star.utils;

import com.example.ringo_star.data.entity.User;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.util.Values;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Updates the profile portion of the graph without discarding questionnaire history. */
public final class ProfileGraphUpdater {
    private static final DateTimeFormatter RECORD_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ProfileGraphUpdater() {}

    /** Return a new model so failure to persist it does not mutate the caller's snapshot. */
    public static Model apply(Model original, User user, LocalDate date) {
        if (user == null) throw new IllegalArgumentException("Profile is required");
        Model updated = new LinkedHashModel();
        if (original != null) updated.addAll(original);

        replace(updated, RingoStarRDF4J.propertyFirstnameIRI, Values.literal(user.getFirstname()));
        replace(updated, RingoStarRDF4J.propertyLastnameIRI, Values.literal(user.getLastname()));
        replace(updated, RingoStarRDF4J.propertyGenderIRI, Values.literal(user.getGender()));
        replace(updated, RingoStarRDF4J.propertyBirthdayIRI, Values.literal(user.getBirthday()));
        replace(updated, RingoStarRDF4J.propertyHeightIRI, Values.literal(user.getHeight()));
        replace(updated, RingoStarRDF4J.propertyBloodGroupIRI, Values.literal(user.getBloodGroup()));

        appendRecordIfChanged(updated, "smokeRecord", RingoStarRDF4J.propertyLastSmokeRecordIRI,
                String.valueOf(user.isSmoke()), Values.literal(user.isSmoke()), date);
        appendRecordIfChanged(updated, "weightRecord", RingoStarRDF4J.propertyLastWeightRecordIRI,
                String.valueOf(user.getWeight()), Values.literal(user.getWeight()), date);
        return updated;
    }

    private static void replace(Model model, IRI predicate, org.eclipse.rdf4j.model.Value value) {
        model.remove(RingoStarRDF4J.nodeUserIRI, predicate, null);
        model.add(RingoStarRDF4J.nodeUserIRI, predicate, value);
    }

    private static void appendRecordIfChanged(Model model, String prefix, IRI pointer,
                                               String current, org.eclipse.rdf4j.model.Value value,
                                               LocalDate date) {
        Optional<String> previousNode = RingoStarRDF4J.getLiteralValue(
                model, RingoStarRDF4J.nodeUserIRI, pointer);
        if (previousNode.isPresent()) {
            IRI iri = Values.iri(RingoStarRDF4J.nodeNS, previousNode.get());
            Optional<String> previous = RingoStarRDF4J.getLiteralValue(model, iri, RingoStarRDF4J.propertyValue);
            if (previous.isPresent() && Objects.equals(previous.get(), current)) return;
        }
        String name = prefix + UUID.randomUUID();
        IRI record = Values.iri(RingoStarRDF4J.nodeNS, name);
        model.add(RingoStarRDF4J.nodeUserIRI, RingoStarRDF4J.relationCarry, record);
        replace(model, pointer, Values.literal(name));
        model.add(record, RingoStarRDF4J.propertyValue, value);
        model.add(record, RingoStarRDF4J.propertyDate, Values.literal(date.format(RECORD_DATE)));
    }
}
