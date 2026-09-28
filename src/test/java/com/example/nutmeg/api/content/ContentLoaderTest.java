package com.example.nutmeg.api.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** If you edit the XML and break it, this fails before your mates find out. */
class ContentLoaderTest {

    @Test
    void realContentLoadsAndValidates() {
        Content content = ContentLoader.fromClasspath().load();
        assertTrue(content.imposterClusters().size() >= 3);
        assertTrue(content.tenLists().size() >= 3);
        assertTrue(content.holCategories().size() >= 3);
        assertTrue(content.headsUpCards().size() >= 30);
        content.tenLists().forEach(list -> assertEquals(10, list.answers().size(), list.question()));
    }
}
