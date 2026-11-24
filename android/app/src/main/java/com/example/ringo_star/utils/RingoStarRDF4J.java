package com.example.ringo_star.utils;

import android.content.Context;
import android.util.Log;
import com.example.ringo_star.security.SecureFiles;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParserRegistry;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.turtle.TurtleParserFactory;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.Literal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RingoStarRDF4J {
    public static String ns = "http://example.org/";

    public static String nodeNS = ns + "node";
    public static String propertyNS = ns + "property";
    public static String relationNS = ns + "relation";

    public static IRI nodeUserIRI = Values.iri(nodeNS, "user");

    public static IRI propertyFirstnameIRI = Values.iri(propertyNS, "firstname");
    public static IRI propertyLastnameIRI = Values.iri(propertyNS, "lastname");
    public static IRI propertyGenderIRI = Values.iri(propertyNS, "gender");
    public static IRI propertyBirthdayIRI = Values.iri(propertyNS, "birthday");
    public static IRI propertyHeightIRI = Values.iri(propertyNS, "height");
    public static IRI propertyBloodGroupIRI = Values.iri(propertyNS, "bloodGroup");
    public static IRI propertyLastSmokeRecordIRI = Values.iri(propertyNS, "lastSmokeRecord");
    public static IRI propertyLastWeightRecordIRI = Values.iri(propertyNS, "lastWeightRecord");
    public static IRI propertyValue = Values.iri(propertyNS, "value");
    public static IRI propertyDate = Values.iri(propertyNS, "date");
    public static IRI propertyName = Values.iri(propertyNS, "name");
    public static IRI propertyText = Values.iri(propertyNS, "text");

    public static IRI relationCarry = Values.iri(relationNS, "carry");
    public static IRI relationCompile = Values.iri(relationNS, "compile");
    public static IRI relationHas = Values.iri(relationNS, "has");

    public static Optional<String> getLiteralValue(Model model, IRI node, IRI property) {
        return model.filter(node, property, null)
                .stream()
                .map(Statement::getObject)
                .filter(Value::isLiteral)
                .map(value -> ((Literal) value).stringValue())
                .findFirst();
    }

    public static Value getFirstNodeRelation(Model model, IRI node, IRI predicate) {
        return model.filter(node, predicate, null)
                .stream()
                .map(Statement::getObject)
                .findFirst()
                .orElse(null);
    }

    public static Set<Value> getAllNodeRelations(Model model, IRI node, IRI predicate) {
        return model.filter(node, predicate, null)
                .stream()
                .map(Statement::getObject)
                .collect(Collectors.toSet());
    }

    /** Persist the graph as authenticated ciphertext, never as a new plaintext Turtle file. */
    public static void saveModelToFile(Model model, Context context, String filePath) {
        try {
            SecureFiles.write(context, filePath, out -> Rio.write(model, out, RDFFormat.TURTLE));
            File legacy = new File(context.getFilesDir(), filePath);
            if (legacy.exists() && !legacy.delete()) {
                throw new IOException("Legacy plaintext graph could not be removed");
            }
        } catch (IOException error) {
            Log.e("RingoStar", "Unable to save encrypted health graph");
            throw new IllegalStateException("Unable to save protected knowledge graph", error);
        }
    }

    public static boolean fileExists(Context context, String filePath) {
        return SecureFiles.exists(context, filePath)
                || new File(context.getFilesDir(), filePath).isFile();
    }

    public static Model loadModelFromFile(Context context, String filePath) {
        try {
            RDFParserRegistry.getInstance().add(new TurtleParserFactory());
            if (SecureFiles.exists(context, filePath)) {
                byte[] plaintext = SecureFiles.read(context, filePath);
                Model model = Rio.parse(new ByteArrayInputStream(plaintext), "", RDFFormat.TURTLE);
                // Remove any leftover plaintext copy from an interrupted migration.
                File legacy = new File(context.getFilesDir(), filePath);
                if (legacy.exists() && !legacy.delete()) {
                    throw new IOException("Unable to remove legacy plaintext graph");
                }
                return model;
            }
            File legacy = new File(context.getFilesDir(), filePath);
            if (!legacy.isFile()) return null;
            Model model;
            try (FileInputStream input = new FileInputStream(legacy)) {
                model = Rio.parse(input, "", RDFFormat.TURTLE);
            }
            // The encrypted copy is committed before the unencrypted original is removed.
            saveModelToFile(model, context, filePath);
            return model;
        } catch (Exception error) {
            Log.e("RingoStar", "Cannot read protected knowledge graph");
            // Do not treat corruption/key loss as an empty graph and overwrite health records.
            throw new IllegalStateException("Protected knowledge graph unavailable", error);
        }
    }

    public static String stringifyModel(Model model) {
        StringWriter writer = new StringWriter();
        Rio.write(model, writer, RDFFormat.TURTLE);

        return writer.toString();
    }

    public static String promptifyModel(Model model) {
        if (model == null) return "";
        String prompt = "";

        String firstname = "";
        String lastname = "";
        String gender = "";
        String birthday = "";
        String height = "";
        String bloodGroup = "";

        StringBuilder weightRecords = new StringBuilder();
        StringBuilder smokeRecords = new StringBuilder();

        StringBuilder questionnaires = new StringBuilder();

        for(Statement statement : model) {
            if(Objects.equals(statement.getPredicate().stringValue(), propertyFirstnameIRI.stringValue()))
                firstname = statement.getObject().stringValue();
            else if(Objects.equals(statement.getPredicate().stringValue(), propertyLastnameIRI.stringValue()))
                lastname = statement.getObject().stringValue();
            else if(Objects.equals(statement.getPredicate().stringValue(), propertyGenderIRI.stringValue()))
                gender = statement.getObject().stringValue();
            else if(Objects.equals(statement.getPredicate().stringValue(), propertyBirthdayIRI.stringValue()))
                birthday = statement.getObject().stringValue();
            else if(Objects.equals(statement.getPredicate().stringValue(), propertyHeightIRI.stringValue()))
                height = statement.getObject().stringValue() + " cm";
            else if(Objects.equals(statement.getPredicate().stringValue(), propertyBloodGroupIRI.stringValue())) {
                bloodGroup = statement.getObject().stringValue()
                        .replace("zero", "0")
                        .replace("_positive", "+")
                        .replace("_negative", "-");
            } else if(Objects.equals(statement.getPredicate().stringValue(), relationCarry.stringValue())) {
                Value valueNode = getFirstNodeRelation(model, (IRI) statement.getObject(), propertyValue);
                Value dateNode = getFirstNodeRelation(model, (IRI) statement.getObject(), propertyDate);
                if (valueNode == null || dateNode == null) continue;
                String value = valueNode.stringValue();
                String date = dateNode.stringValue();

                if(value.equals("true") || value.equals("false")) {
                    String smoke = "";

                    switch(value) {
                        case "true":
                            smoke = "smokes";
                            break;

                        case "false":
                            smoke = "doesn't smoke";
                            break;
                    }

                    smokeRecords.append(", in ").append(date).append(" ").append(smoke);
                } else {
                    weightRecords.append(", weight ").append(value).append(" kg in ").append(date);
                }
            } else if(Objects.equals(statement.getPredicate().stringValue(), relationCompile.stringValue())) {
                Value nameNode = getFirstNodeRelation(model, (IRI) statement.getObject(), propertyName);
                Value dateNode = getFirstNodeRelation(model, (IRI) statement.getObject(), propertyDate);
                if (nameNode == null || dateNode == null) continue;
                String name = nameNode.stringValue();
                String date = dateNode.stringValue();

                questionnaires.append("In " + date + " compiled a questionnaire called " + name + " with the following results: ");

                Set<Value> relationsSet = getAllNodeRelations(model, (IRI) statement.getObject(), relationHas);

                for(Value relation : relationsSet) {
                    try {
                        String text = getFirstNodeRelation(model, (IRI) relation, propertyText).stringValue();
                        String value = getFirstNodeRelation(model, (IRI) relation, propertyValue).stringValue();

                        questionnaires.append(text + " " + value + ". ");
                    } catch(Exception ignored) {}
                }
            }

            prompt = "The patient name is " + firstname + " " + lastname + ", " + gender.toLowerCase() + ", born in " + birthday + ", height " + height + ", blood group " + bloodGroup + weightRecords + smokeRecords + ". " + questionnaires;
        }

        return prompt;
    }
}