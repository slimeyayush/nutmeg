package com.example.nutmeg.api.content;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.example.nutmeg.api.content.Content.Card;
import com.example.nutmeg.api.content.Content.HolCategory;
import com.example.nutmeg.api.content.Content.HolItem;
import com.example.nutmeg.api.content.Content.TenAnswer;
import com.example.nutmeg.api.content.Content.TenList;
import com.example.nutmeg.api.match.Text;

/**
 * Loads the game "database": four XML files on the classpath under /content.
 *
 * Validation is strict and happens at startup. A TEN list with nine answers
 * should stop the server on your laptop, not surface mid-game in front of
 * your mates.
 */
public final class ContentLoader {

    public interface Source {
        InputStream open(String path) throws IOException;
    }

    private final Source source;

    public ContentLoader(Source source) {
        this.source = source;
    }

    /** Reads from the classpath — works in the IDE, in bootRun, and inside the JAR. */
    public static ContentLoader fromClasspath() {
        return new ContentLoader(path -> {
            InputStream in = ContentLoader.class.getResourceAsStream("/" + path);
            if (in == null) {
                throw new IOException("Missing classpath resource: " + path);
            }
            return in;
        });
    }

    public Content load() {
        return new Content(
                loadImposter(),
                loadTen(),
                loadHigherLower(),
                loadHeadsUp());
    }

    // ------------------------------------------------------------- imposter

    private List<List<String>> loadImposter() {
        Element root = read("content/imposter.xml");
        List<List<String>> clusters = new ArrayList<>();
        for (Element cluster : children(root, "cluster")) {
            List<String> names = new ArrayList<>();
            for (Element player : children(cluster, "player")) {
                String name = player.getTextContent().strip();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
            if (names.size() < 2) {
                throw invalid("imposter.xml: every <cluster> needs at least 2 players");
            }
            clusters.add(List.copyOf(names));
        }
        if (clusters.size() < 3) {
            throw invalid("imposter.xml: needs at least 3 clusters");
        }
        return List.copyOf(clusters);
    }

    // ------------------------------------------------------------------ ten

    private List<TenList> loadTen() {
        Element root = read("content/ten.xml");
        List<TenList> lists = new ArrayList<>();

        for (Element list : children(root, "list")) {
            String question = attr(list, "question");
            boolean people = !"teams".equalsIgnoreCase(list.getAttribute("kind"));

            List<TenAnswer> answers = new ArrayList<>();
            Set<Integer> seenPoints = new HashSet<>();

            for (Element answer : children(list, "answer")) {
                String display = answer.getTextContent().strip();
                int points = Integer.parseInt(attr(answer, "points"));
                if (points < 1 || points > 10 || !seenPoints.add(points)) {
                    throw invalid("ten.xml: \"" + question + "\" needs points 1..10, each used once");
                }
                answers.add(new TenAnswer(display, points,
                        Text.keysFor(display, aliases(answer), people)));
            }

            if (answers.size() != 10) {
                throw invalid("ten.xml: \"" + question + "\" has " + answers.size()
                        + " answers, needs exactly 10");
            }
            answers.sort((a, b) -> Integer.compare(a.points(), b.points()));
            lists.add(new TenList(question, List.copyOf(answers)));
        }
        if (lists.size() < 3) {
            throw invalid("ten.xml: needs at least 3 lists");
        }
        return List.copyOf(lists);
    }

    // --------------------------------------------------------- higher/lower

    private List<HolCategory> loadHigherLower() {
        Element root = read("content/higherlower.xml");
        List<HolCategory> categories = new ArrayList<>();

        for (Element category : children(root, "category")) {
            List<HolItem> items = new ArrayList<>();
            for (Element item : children(category, "item")) {
                items.add(new HolItem(
                        item.getTextContent().strip(),
                        Long.parseLong(attr(item, "value"))));
            }
            if (items.size() < 8) {
                throw invalid("higherlower.xml: \"" + attr(category, "label")
                        + "\" needs at least 8 items");
            }
            categories.add(new HolCategory(
                    attr(category, "label"),
                    attr(category, "question"),
                    attr(category, "higher"),
                    attr(category, "lower"),
                    List.copyOf(items)));
        }
        if (categories.size() < 3) {
            throw invalid("higherlower.xml: needs at least 3 categories");
        }
        return List.copyOf(categories);
    }

    // -------------------------------------------------------------- heads up

    private List<Card> loadHeadsUp() {
        Element root = read("content/headsup.xml");
        List<Card> cards = new ArrayList<>();
        for (Element card : children(root, "card")) {
            String name = card.getTextContent().strip();
            String kind = card.getAttribute("kind").isBlank() ? "player" : card.getAttribute("kind");
            cards.add(new Card(name, kind,
                    Text.keysFor(name, aliases(card), "player".equals(kind) || "manager".equals(kind))));
        }
        if (cards.size() < 30) {
            throw invalid("headsup.xml: needs at least 30 cards");
        }
        return List.copyOf(cards);
    }

    // --------------------------------------------------------------- helpers

    private Element read(String path) {
        try (InputStream in = source.open(path)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // No DTDs, no external entities: the standard XXE lockdown.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(in);
            return document.getDocumentElement();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read " + path + ": " + ex.getMessage(), ex);
        }
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && tag.equals(element.getTagName())) {
                out.add(element);
            }
        }
        return out;
    }

    private static String attr(Element element, String name) {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) {
            throw invalid("<" + element.getTagName() + "> is missing attribute \"" + name + "\"");
        }
        return value.strip();
    }

    private static List<String> aliases(Element element) {
        String raw = element.getAttribute("aliases");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\\|"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("Content error — " + message);
    }
}
