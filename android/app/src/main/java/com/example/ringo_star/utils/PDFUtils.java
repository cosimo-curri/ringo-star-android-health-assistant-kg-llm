package com.example.ringo_star.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import com.example.ringo_star.R;
import com.example.ringo_star.security.SecureFiles;
import com.example.ringo_star.data.entity.User;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

/** Android-native PDF generation; reports remain encrypted until explicit export. */
public final class PDFUtils {
    private PDFUtils() {}

    public static void createPdf(Context context, User user, String llmResponse) throws IOException {
        if (user == null || llmResponse == null) throw new IOException("Report data unavailable");
        Model model = RingoStarRDF4J.loadModelFromFile(context, "kg.ttl");
        if (model == null) throw new IOException("Knowledge graph unavailable");

        PdfDocument pdf = new PdfDocument();
        try {
            PageWriter page = new PageWriter(pdf);
            Bitmap logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.logo_pdf);
            if (logo != null) {
                page.logo(logo);
                logo.recycle();
            }
            page.heading("User information");
            page.line(safe(user.getFirstname()) + " " + safe(user.getLastname()));
            page.line(capitalizeFirstLetter(user.getGender() == null ? "" : user.getGender().toString()));
            page.line(user.getBirthday() == null ? "" : user.getBirthday().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            page.line(user.getHeight() + " cm");
            page.line(user.getWeight() + " kg");
            page.line(user.getBloodGroup() == null ? "" : user.getBloodGroup().toString()
                    .replace("zero", "0").replace("_positive", "+").replace("_negative", "-"));
            page.line(user.isSmoke() ? "Smoke" : "Doesn't smoke");
            page.heading("LLM response");
            page.line(llmResponse);
            page.heading("Graph information (about user, questionnaires and variations of weight, smoke, etc.)");
            int index = 1;
            for (Statement statement : model) {
                page.line(index++ + ") " + statement.getSubject().stringValue() + " "
                        + statement.getPredicate().stringValue() + " " + statement.getObject().stringValue());
            }
            page.finish();
            // Keep the report encrypted and private until the user explicitly exports it.
            SecureFiles.write(context, "report.pdf", pdf::writeTo);
        } finally {
            pdf.close();
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }

    public static String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }

    /** Produces a paginated, word-wrapped PDF with profile, model response, and graph statements. */
    private static final class PageWriter {
        private static final int PAGE_WIDTH = 595;
        private static final int PAGE_HEIGHT = 842;
        private static final int MARGIN = 42;
        private static final int LINE_HEIGHT = 17;
        private final PdfDocument pdf;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private PdfDocument.Page page;
        private Canvas canvas;
        private int pageNumber = 0;
        private float y;

        PageWriter(PdfDocument pdf) { this.pdf = pdf; newPage(); }

        private void newPage() {
            if (page != null) pdf.finishPage(page);
            page = pdf.startPage(new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create());
            canvas = page.getCanvas();
            y = MARGIN;
            paint.setColor(android.graphics.Color.BLACK);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            paint.setTextSize(11);
        }

        private void ensureSpace(int required) {
            if (y + required > PAGE_HEIGHT - MARGIN) newPage();
        }

        void logo(Bitmap bitmap) {
            ensureSpace(90);
            Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(bitmap, null, new android.graphics.RectF(MARGIN, y, MARGIN + 235, y + 80), bitmapPaint);
            y += 96;
        }

        void heading(String title) {
            y += 12;
            paint.setTextSize(16);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            line(title);
            y += 4;
            paint.setTextSize(11);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        }

        void line(String value) {
            String text = value == null ? "" : value.replace("\r", "");
            for (String paragraph : text.split("\n", -1)) {
                if (paragraph.isEmpty()) {
                    ensureSpace(LINE_HEIGHT); y += LINE_HEIGHT;
                    continue;
                }
                StringBuilder current = new StringBuilder();
                for (String word : paragraph.split("\\s+")) {
                    String candidate = current.length() == 0 ? word : current + " " + word;
                    if (paint.measureText(candidate) > PAGE_WIDTH - MARGIN * 2 && current.length() > 0) {
                        draw(current.toString());
                        current.setLength(0);
                    }
                    // Long IRIs in Turtle statements can exceed one PDF line.
                    String remaining = word;
                    while (paint.measureText(remaining) > PAGE_WIDTH - MARGIN * 2) {
                        int split = remaining.length();
                        while (split > 1 && paint.measureText(remaining.substring(0, split)) > PAGE_WIDTH - MARGIN * 2) split--;
                        if (current.length() > 0) { draw(current.toString()); current.setLength(0); }
                        draw(remaining.substring(0, split));
                        remaining = remaining.substring(split);
                    }
                    if (!remaining.isEmpty()) {
                        if (current.length() > 0) current.append(' ');
                        current.append(remaining);
                    }
                }
                if (current.length() > 0) draw(current.toString());
            }
        }

        private void draw(String text) {
            ensureSpace(LINE_HEIGHT);
            canvas.drawText(text, MARGIN, y + 12, paint);
            y += LINE_HEIGHT;
        }

        void finish() {
            if (page != null) { pdf.finishPage(page); page = null; }
        }
    }
}
