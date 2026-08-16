package com.zenith.discord;

import org.junit.jupiter.api.Test;

import static com.zenith.Globals.CONFIG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordEmbedDecorationTest {
    @Test
    void noAuthorRendersConfiguredAccountLabel() {
        withUsername("test-account", () -> {
            var author = Embed.builder().title("Disconnected").toJDAEmbed().getAuthor();

            assertNotNull(author);
            assertEquals("Account: test-account", author.getName());
        });
    }

    @Test
    void existingAuthorMetadataIsPreservedInRenderedEmbed() {
        withUsername("test-account", () -> {
            var original = new Embed.Author("Reconnect monitor", "https://example.invalid/status", "https://example.invalid/icon.png");
            var embed = Embed.builder().author(original).footer("Original footer", "https://example.invalid/footer.png");
            var author = embed.toJDAEmbed().getAuthor();

            assertNotNull(author);
            assertEquals("Account: test-account | Reconnect monitor", author.getName());
            assertEquals(original.url(), author.getUrl());
            assertEquals(original.iconUrl(), author.getIconUrl());
            assertEquals("Original footer", embed.toJDAEmbed().getFooter().getText());
            assertEquals(original, embed.author(), "serialization must not decorate the mutable source embed");
        });
    }

    @Test
    void reusedEmbedUsesOnlyCurrentAccountLabel() {
        var embed = Embed.builder().author(new Embed.Author("Original", "https://example.invalid/status", "https://example.invalid/icon.png"));

        withUsername("old", () -> assertEquals("Account: old | Original", embed.toJDAEmbed().getAuthor().getName()));
        withUsername("new", () -> {
            var authorName = embed.toJDAEmbed().getAuthor().getName();
            assertEquals("Account: new | Original", authorName);
            assertFalse(authorName.contains("old"));
        });
    }

    @Test
    void naturalAccountPrefixInOriginalAuthorIsPreserved() {
        withUsername("instance", () -> {
            var author = Embed.builder().author(new Embed.Author("Account: status", null, null)).toJDAEmbed().getAuthor();

            assertNotNull(author);
            assertEquals("Account: instance | Account: status", author.getName());
        });
    }

    @Test
    void nullOriginalAuthorNameUsesAccountLabelAndPreservesMetadata() {
        withUsername("test-account", () -> {
            var author = Embed.builder().author(new Embed.Author(null, "https://example.invalid/status", "https://example.invalid/icon.png"))
                .toJDAEmbed()
                .getAuthor();

            assertNotNull(author);
            assertEquals("Account: test-account", author.getName());
            assertEquals("https://example.invalid/status", author.getUrl());
            assertEquals("https://example.invalid/icon.png", author.getIconUrl());
        });
    }

    @Test
    void renderedDecoratedAuthorIsLimitedToJdaMaximum() {
        withUsername("test-account", () -> {
            var author = Embed.builder().author(new Embed.Author(
                "x".repeat(300),
                "https://example.invalid/status",
                "https://example.invalid/icon.png")).toJDAEmbed().getAuthor();

            assertNotNull(author);
            assertEquals(256, author.getName().length());
            assertEquals("https://example.invalid/status", author.getUrl());
            assertEquals("https://example.invalid/icon.png", author.getIconUrl());
        });
    }

    @Test
    void accountLabelKeepsRenderedEmbedWithinTotalCharacterLimit() {
        withUsername("test-account", () -> {
            var rendered = Embed.builder()
                .title("t".repeat(256))
                .description("d".repeat(4096))
                .addField("a", "v".repeat(1024))
                .addField("b", "v".repeat(622))
                .toJDAEmbed();

            assertNotNull(rendered.getAuthor());
            assertEquals("Account: test-account", rendered.getAuthor().getName());
            assertTrue(rendered.getLength() <= 6000, "the account label must be included in the total embed budget");
        });
    }

    private static void withUsername(final String username, final Runnable test) {
        final String originalUsername = CONFIG.authentication.username;
        try {
            CONFIG.authentication.username = username;
            test.run();
        } finally {
            CONFIG.authentication.username = originalUsername;
        }
    }
}
