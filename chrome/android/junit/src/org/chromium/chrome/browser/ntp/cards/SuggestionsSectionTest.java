// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import static org.chromium.chrome.browser.ntp.cards.ContentSuggestionsTestUtils.bindViewHolders;
import static org.chromium.chrome.browser.ntp.cards.ContentSuggestionsTestUtils.createDummySuggestions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.Config;

import org.chromium.base.Callback;
import org.chromium.base.test.util.Feature;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.EnableFeatures;
import org.chromium.chrome.browser.ntp.cards.ContentSuggestionsTestUtils.CategoryInfoBuilder;
import org.chromium.chrome.browser.ntp.snippets.CategoryStatus;
import org.chromium.chrome.browser.ntp.snippets.KnownCategories;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.ntp.snippets.SuggestionsSource;
import org.chromium.chrome.browser.offlinepages.OfflinePageItem;
import org.chromium.chrome.browser.suggestions.SuggestionsMetricsReporter;
import org.chromium.chrome.browser.suggestions.SuggestionsNavigationDelegate;
import org.chromium.chrome.browser.suggestions.SuggestionsRanker;
import org.chromium.chrome.browser.suggestions.SuggestionsUiDelegate;
import org.chromium.testing.local.LocalRobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Unit tests for {@link SuggestionsSection}.
 */
@RunWith(LocalRobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SuggestionsSectionTest {
    @Mock
    private SuggestionsSection.Delegate mDelegate;
    @Mock
    private NodeParent mParent;
    @Mock
    private SuggestionsUiDelegate mUiDelegate;
    private FakeOfflinePageBridge mBridge;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mBridge = new FakeOfflinePageBridge();

        // Set empty variation params for the test.
        CardsVariationParameters.setTestVariationParams(new HashMap<String, String>());
    }

    @Test
    @Feature({"Ntp"})
    public void testDismissSibling() {
        List<SnippetArticle> snippets = createDummySuggestions(3);
        SuggestionsSection section = createSectionWithReloadAction(true);

        section.setStatus(CategoryStatus.AVAILABLE);
        assertNotNull(section.getActionItem());

        // Without snippets.
        assertEquals(ItemViewType.ACTION, section.getItemViewType(2));
        assertEquals(-1, section.getDismissSiblingPosDelta(2));
        assertEquals(ItemViewType.STATUS, section.getItemViewType(1));
        assertEquals(1, section.getDismissSiblingPosDelta(1));

        // With snippets.
        section.setSuggestions(snippets, CategoryStatus.AVAILABLE, /* replaceExisting = */ true);
        assertEquals(ItemViewType.SNIPPET, section.getItemViewType(1));
        assertEquals(0, section.getDismissSiblingPosDelta(1));
    }

    @Test
    @Feature({"Ntp"})
    public void testAddSuggestionsNotification() {
        final int suggestionCount = 5;
        List<SnippetArticle> snippets = createDummySuggestions(suggestionCount);

        SuggestionsSection section = createSectionWithReloadAction(false);
        // Simulate initialisation by the adapter. Here we don't care about the notifications, since
        // the RecyclerView will be updated through notifyDataSetChanged.
        section.setStatus(CategoryStatus.AVAILABLE);
        reset(mParent);

        assertEquals(2, section.getItemCount()); // When empty, we have the header and status card.
        assertEquals(ItemViewType.STATUS, section.getItemViewType(1));

        section.setSuggestions(snippets, CategoryStatus.AVAILABLE, /* replaceExisting = */ true);
        verify(mParent).onItemRangeInserted(section, 1, suggestionCount);
        verify(mParent).onItemRangeRemoved(section, 1 + suggestionCount, 1);
    }

    @Test
    @Feature({"Ntp"})
    public void testSetStatusNotification() {
        final int suggestionCount = 5;
        List<SnippetArticle> snippets = createDummySuggestions(suggestionCount);
        SuggestionsSection section = createSectionWithReloadAction(false);

        // Simulate initialisation by the adapter. Here we don't care about the notifications, since
        // the RecyclerView will be updated through notifyDataSetChanged.
        section.setSuggestions(snippets, CategoryStatus.AVAILABLE, /* replaceExisting = */ true);
        reset(mParent);

        // We don't clear suggestions when the status is AVAILABLE.
        section.setStatus(CategoryStatus.AVAILABLE);
        verifyNoMoreInteractions(mParent);

        // We clear existing suggestions when the status is not AVAILABLE, and show the status card.
        section.setStatus(CategoryStatus.SIGNED_OUT);
        verify(mParent).onItemRangeRemoved(section, 1, suggestionCount);
        verify(mParent).onItemRangeInserted(section, 1, 1);

        // A loading state item triggers showing the loading item.
        section.setStatus(CategoryStatus.AVAILABLE_LOADING);
        verify(mParent).onItemRangeInserted(section, 2, 1);

        section.setStatus(CategoryStatus.AVAILABLE);
        verify(mParent).onItemRangeRemoved(section, 2, 1);
        verifyNoMoreInteractions(mParent);
    }

    @Test
    @Feature({"Ntp"})
    public void testRemoveUnknownSuggestion() {
        SuggestionsSection section = createSectionWithReloadAction(false);
        section.setStatus(CategoryStatus.AVAILABLE);
        section.removeSuggestionById("foobar");
    }

    @Test
    @Feature({"Ntp"})
    public void testRemoveSuggestionNotification() {
        final int suggestionCount = 2;
        List<SnippetArticle> snippets = createDummySuggestions(suggestionCount);

        SuggestionsSection section = createSectionWithReloadAction(false);
        section.setStatus(CategoryStatus.AVAILABLE);
        reset(mParent);

        section.setSuggestions(snippets, CategoryStatus.AVAILABLE, /* replaceExisting = */ true);

        section.removeSuggestionById(snippets.get(1).mIdWithinCategory);
        verify(mParent).onItemRangeRemoved(section, 2, 1);

        section.removeSuggestionById(snippets.get(0).mIdWithinCategory);
        verify(mParent).onItemRangeRemoved(section, 1, 1);
        verify(mParent).onItemRangeInserted(section, 1, 1);

        assertEquals(2, section.getItemCount());
        assertEquals(ItemViewType.STATUS, section.getItemViewType(1));
    }

    @Test
    @Feature({"Ntp"})
    public void testRemoveSuggestionNotificationWithButton() {
        final int suggestionCount = 2;
        List<SnippetArticle> snippets = createDummySuggestions(suggestionCount);

        SuggestionsCategoryInfo info =
                new CategoryInfoBuilder(42)
                .withMoreAction()
                .withReloadAction()
                .showIfEmpty()
                .build();
        SuggestionsSection section = createSection(info);
        section.setStatus(CategoryStatus.AVAILABLE);
        reset(mParent);
        assertEquals(3, section.getItemCount()); // We have the header and status card and a button.

        section.setSuggestions(snippets, CategoryStatus.AVAILABLE, /* replaceExisting = */ true);
        assertEquals(4, section.getItemCount());

        section.removeSuggestionById(snippets.get(0).mIdWithinCategory);
        verify(mParent).onItemRangeRemoved(section, 1, 1);

        section.removeSuggestionById(snippets.get(1).mIdWithinCategory);
        verify(mParent, times(2)).onItemRangeRemoved(section, 1, 1);
        verify(mParent).onItemRangeInserted(section, 1, 1); // Only the status card is added.
        assertEquals(3, section.getItemCount());
        assertEquals(ItemViewType.STATUS, section.getItemViewType(1));
        assertEquals(ItemViewType.ACTION, section.getItemViewType(2));
    }

    @Test
    @Feature({"Ntp"})
    @EnableFeatures({ChromeFeatureList.NTP_SUGGESTIONS_SECTION_DISMISSAL})
    public void testDismissSection() {
        SuggestionsSection section = createSectionWithReloadAction(false);
        section.setStatus(CategoryStatus.AVAILABLE);
        reset(mParent);
        assertEquals(2, section.getItemCount());

        @SuppressWarnings("unchecked")
        Callback<String> callback = mock(Callback.class);
        section.dismissItem(1, callback);
        verify(mDelegate).dismissSection(section);
        verify(callback).onResult(section.getHeaderText());
    }

    @Test
    @Feature({"Ntp"})
    public void testOfflineStatus() {
        final int suggestionCount = 3;
        final List<SnippetArticle> snippets = createDummySuggestions(suggestionCount);
        assertNull(snippets.get(0).getOfflinePageOfflineId());
        assertNull(snippets.get(1).getOfflinePageOfflineId());
        assertNull(snippets.get(2).getOfflinePageOfflineId());

        final OfflinePageItem item0 = createOfflinePageItem(snippets.get(0).mUrl, 0L);
        final OfflinePageItem item1 = createOfflinePageItem(snippets.get(1).mUrl, 1L);

        mBridge.setIsOfflinePageModelLoaded(true);
        mBridge.setItems(Arrays.asList(item0, item1));

        SuggestionsSection section = createSectionWithReloadAction(true);
        section.setSuggestions(snippets, CategoryStatus.AVAILABLE, /* replaceExisting = */ true);

        // Check that we pick up the correct information.
        assertEquals(Long.valueOf(0L), snippets.get(0).getOfflinePageOfflineId());
        assertEquals(Long.valueOf(1L), snippets.get(1).getOfflinePageOfflineId());
        assertNull(snippets.get(2).getOfflinePageOfflineId());

        final OfflinePageItem item2 = createOfflinePageItem(snippets.get(2).mUrl, 2L);

        mBridge.setItems(Arrays.asList(item1, item2));

        // Check that a change in OfflinePageBridge state forces an update.
        mBridge.fireOfflinePageModelLoaded();
        assertNull(snippets.get(0).getOfflinePageOfflineId());
        assertEquals(Long.valueOf(1L), snippets.get(1).getOfflinePageOfflineId());
        assertEquals(Long.valueOf(2L), snippets.get(2).getOfflinePageOfflineId());
    }

    @Test
    @Feature({"Ntp"})
    public void testViewAllActionPriority() {
        // When all the actions are enabled, ViewAll always has the priority and is shown.

        // Spy so that VerifyAction can check methods being called.
        SuggestionsCategoryInfo info =
                spy(new CategoryInfoBuilder(42)
                        .withMoreAction()
                        .withReloadAction()
                        .withViewAllAction()
                        .showIfEmpty()
                        .build());
        SuggestionsSection section = createSection(info);

        assertTrue(section.getActionItem().isVisible());
        verifyAction(section, ActionItem.ACTION_VIEW_ALL);

        section.setSuggestions(
                createDummySuggestions(3), CategoryStatus.AVAILABLE, /* replaceExisting = */ true);

        assertTrue(section.getActionItem().isVisible());
        verifyAction(section, ActionItem.ACTION_VIEW_ALL);
    }

    @Test
    @Feature({"Ntp"})
    public void testReloadAndFetchMoreActionPriority() {
        // When both Reload and FetchMore are enabled, FetchMore runs when we have suggestions, and
        // Reload when we don't.

        // Spy so that VerifyAction can check methods being called.
        SuggestionsCategoryInfo info =
                spy(new CategoryInfoBuilder(42)
                        .withMoreAction()
                        .withReloadAction()
                        .showIfEmpty()
                        .build());
        SuggestionsSection section = createSection(info);

        assertTrue(section.getActionItem().isVisible());
        verifyAction(section, ActionItem.ACTION_RELOAD);

        section.setSuggestions(
                createDummySuggestions(3), CategoryStatus.AVAILABLE, /* replaceExisting = */ true);

        assertTrue(section.getActionItem().isVisible());
        verifyAction(section, ActionItem.ACTION_FETCH_MORE);
    }

    @Test
    @Feature({"Ntp"})
    public void testReloadActionPriority() {
        // When only Reload is enabled, it only shows when we have no suggestions.

        // Spy so that VerifyAction can check methods being called.
        SuggestionsCategoryInfo info =
                spy(new CategoryInfoBuilder(42).withReloadAction().showIfEmpty().build());
        SuggestionsSection section = createSection(info);

        assertTrue(section.getActionItem().isVisible());
        verifyAction(section, ActionItem.ACTION_RELOAD);

        section.setSuggestions(
                createDummySuggestions(3), CategoryStatus.AVAILABLE, /* replaceExisting = */ true);

        assertFalse(section.getActionItem().isVisible());
        verifyAction(section, ActionItem.ACTION_NONE);
    }

    @Test
    @Feature({"Ntp"})
    public void testFetchMoreActionPriority() {
        // When only FetchMore is enabled, it only shows when we have suggestions.

        // Spy so that VerifyAction can check methods being called.
        SuggestionsCategoryInfo info =
                spy(new CategoryInfoBuilder(42).withMoreAction().showIfEmpty().build());
        SuggestionsSection section = createSection(info);

        assertFalse(section.getActionItem().isVisible());
        verifyAction(section, ActionItem.ACTION_NONE);

        section.setSuggestions(
                createDummySuggestions(3), CategoryStatus.AVAILABLE, /* replaceExisting = */ true);

        assertTrue(section.getActionItem().isVisible());
        verifyAction(section, ActionItem.ACTION_FETCH_MORE);
    }

    @Test
    @Feature({"Ntp"})
    public void testNoAction() {
        // Test where no action is enabled.

        // Spy so that VerifyAction can check methods being called.
        SuggestionsCategoryInfo info = spy(new CategoryInfoBuilder(42).showIfEmpty().build());
        SuggestionsSection section = createSection(info);

        assertFalse(section.getActionItem().isVisible());
        verifyAction(section, ActionItem.ACTION_NONE);

        section.setSuggestions(
                createDummySuggestions(3), CategoryStatus.AVAILABLE, /* replaceExisting = */ true);

        assertFalse(section.getActionItem().isVisible());
        verifyAction(section, ActionItem.ACTION_NONE);
    }

    @Test
    @Feature({"Ntp"})
    public void testFetchMoreProgressDisplay() {
        final int suggestionCount = 3;
        SuggestionsCategoryInfo info =
                spy(new CategoryInfoBuilder(42).withMoreAction().showIfEmpty().build());
        SuggestionsSection section = createSection(info);
        section.setSuggestions(createDummySuggestions(suggestionCount), CategoryStatus.AVAILABLE,
                /* replaceExisting = */ true);
        assertFalse(section.getProgressItemForTesting().isVisible());

        // Tap the button
        verifyAction(section, ActionItem.ACTION_FETCH_MORE);
        assertTrue(section.getProgressItemForTesting().isVisible());

        // Simulate receiving suggestions.
        section.setSuggestions(createDummySuggestions(suggestionCount), CategoryStatus.AVAILABLE,
                /* replaceExisting = */ false);
        assertFalse(section.getProgressItemForTesting().isVisible());
    }

    /**
     * Tests that the UI updates on updated suggestions.
     */
    @Test
    @Feature({"Ntp"})
    public void testSectionUpdatesOnNewSuggestions() {
        SuggestionsSection section = createSectionWithSuggestions(createDummySuggestions(4));
        assertEquals(4, section.getSuggestionsCount());

        section.setSuggestions(
                createDummySuggestions(3), CategoryStatus.AVAILABLE, /* replaceExisting = */ true);
        assertEquals(3, section.getSuggestionsCount());
    }

    /**
     * Tests that the UI does not update when updating is disabled by a parameter.
     */
    @Test
    @Feature({"Ntp"})
    public void testSectionDoesNotUpdateOnNewSuggestionsWhenDisabled() {
        // Override variation params for the test.
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("ignore_updates_for_existing_suggestions", "true");
        CardsVariationParameters.setTestVariationParams(params);

        SuggestionsSection section = createSectionWithSuggestions(createDummySuggestions(4));
        assertEquals(4, section.getSuggestionsCount());

        section.setSuggestions(
                createDummySuggestions(3), CategoryStatus.AVAILABLE, /* replaceExisting = */ true);
        assertEquals(4, section.getSuggestionsCount());
    }

    /**
     * Tests that the UI does not update the first item of the section if it has been viewed.
     */
    @Test
    @Feature({"Ntp"})
    public void testSectionDoesNotUpdateFirstSuggestionOnNewSuggestionsWhenSeen() {
        List<SnippetArticle> snippets = createDummySuggestions(4, KnownCategories.ARTICLES, "old");
        // Copy the list when passing to the section - it may alter it but we later need it.
        SuggestionsSection section =
                createSectionWithSuggestions(new ArrayList<>(snippets));
        assertEquals(4, section.getSuggestionsCount());

        // Bind the first suggestion - indicate that it is being viewed.
        // Indices in {@code section} are off-by-one (index 0 is the header).
        bindViewHolders(section, 1, 2);

        List<SnippetArticle> newSnippets =
                createDummySuggestions(3, KnownCategories.ARTICLES, "new");
        // Copy the list when passing to the section - it may alter it but we later need it.
        section.setSuggestions(new ArrayList<>(newSnippets), CategoryStatus.AVAILABLE,
                /* replaceExisting = */ true);
        assertEquals(3, section.getSuggestionsCount());
        assertEquals(snippets.get(0), section.getSuggestionAt(1));
        assertNotEquals(snippets.get(1), section.getSuggestionAt(2));
        assertEquals(newSnippets.get(0), section.getSuggestionAt(2));
    }

    /**
     * Tests that the UI does not update the first two items of the section if they have been
     * viewed.
     */
    @Test
    @Feature({"Ntp"})
    public void testSectionDoesNotUpdateFirstTwoSuggestionOnNewSuggestionsWhenSeen() {
        List<SnippetArticle> snippets = createDummySuggestions(4, KnownCategories.ARTICLES, "old");
        // Copy the list when passing to the section - it may alter it but we later need it.
        SuggestionsSection section =
                createSectionWithSuggestions(new ArrayList<>(snippets));
        assertEquals(4, section.getSuggestionsCount());

        // Bind the first two suggestions - indicate that they are being viewed.
        // Indices in {@code section} are off-by-one (index 0 is the header).
        bindViewHolders(section, 1, 3);

        List<SnippetArticle> newSnippets =
                createDummySuggestions(3, KnownCategories.ARTICLES, "new");
        // Copy the list when passing to the section - it may alter it but we later need it.
        section.setSuggestions(new ArrayList<>(newSnippets), CategoryStatus.AVAILABLE,
                /* replaceExisting = */ true);
        assertEquals(3, section.getSuggestionsCount());
        assertEquals(snippets.get(0), section.getSuggestionAt(1));
        assertEquals(snippets.get(1), section.getSuggestionAt(2));
        assertNotEquals(snippets.get(2), section.getSuggestionAt(3));
        assertEquals(newSnippets.get(0), section.getSuggestionAt(3));
    }

    /**
     * Tests that the UI does not update any items of the section if the new list is shorter than
     * what has been viewed.
     */
    @Test
    @Feature({"Ntp"})
    public void testSectionDoesNotUpdateOnNewSuggestionsWhenNewListIsShorter() {
        List<SnippetArticle> snippets = createDummySuggestions(4, KnownCategories.ARTICLES, "old");
        // Copy the list when passing to the section - it may alter it but we later need it.
        SuggestionsSection section =
                createSectionWithSuggestions(new ArrayList<>(snippets));
        assertEquals(4, section.getSuggestionsCount());

        // Bind the first two suggestions - indicate that they are being viewed.
        // Indices in {@code section} are off-by-one (index 0 is the header).
        bindViewHolders(section, 1, 3);

        section.setSuggestions(
                createDummySuggestions(1), CategoryStatus.AVAILABLE, /* replaceExisting = */ true);
        // Even though the new list has just one suggestion, we need to keep the two seen ones
        // around.
        assertEquals(2, section.getSuggestionsCount());
        assertEquals(snippets.get(0), section.getSuggestionAt(1));
        assertEquals(snippets.get(1), section.getSuggestionAt(2));
    }

    /**
     * Tests that the UI does not update any items of the section if the current list is shorter
     * than what has been viewed.
     */
    @Test
    @Feature({"Ntp"})
    public void testSectionDoesNotUpdateOnNewSuggestionsWhenCurrentListIsShorter() {
        List<SnippetArticle> snippets = createDummySuggestions(3, KnownCategories.ARTICLES, "old");
        // Copy the list when passing to the section - it may alter it but we later need it.
        SuggestionsSection section =
                createSectionWithSuggestions(new ArrayList<>(snippets));
        assertEquals(3, section.getSuggestionsCount());

        // Bind the first two suggestions - indicate that they are being viewed.
        // Indices in {@code section} are off-by-one (index 0 is the header).
        bindViewHolders(section, 1, 3);

        // Remove last two items.
        section.removeSuggestionById(section.getSuggestionAt(3).mIdWithinCategory);
        section.removeSuggestionById(section.getSuggestionAt(2).mIdWithinCategory);

        assertEquals(1, section.getSuggestionsCount());

        section.setSuggestions(
                createDummySuggestions(4), CategoryStatus.AVAILABLE, /* replaceExisting = */ true);
        // We do not touch the current list if all has been seen.
        assertEquals(1, section.getSuggestionsCount());
        assertEquals(snippets.get(0), section.getSuggestionAt(1));
    }

    /**
     * Tests that the UI does not update when the section has been viewed.
     */
    @Test
    @Feature({"Ntp"})
    public void testSectionDoesNotUpdateOnNewSuggestionsWhenAllSeen() {
        List<SnippetArticle> snippets = createDummySuggestions(4, KnownCategories.ARTICLES, "old");
        SuggestionsSection section = createSectionWithSuggestions(snippets);
        assertEquals(4, section.getSuggestionsCount());

        // Bind all the suggestions - indicate that they are being viewed.
        bindViewHolders(section);

        section.setSuggestions(
                createDummySuggestions(3), CategoryStatus.AVAILABLE, /* replaceExisting = */ true);

        // All old snippets should be in place.
        assertEquals(4, section.getSuggestionsCount());
        int index = 1;
        for (SnippetArticle snippet : snippets) {
            assertEquals(snippet, section.getSuggestionAt(index++));
        }
    }

    private SuggestionsSection createSectionWithSuggestions(List<SnippetArticle> snippets) {
        SuggestionsSection section = createSectionWithReloadAction(true);
        section.setStatus(CategoryStatus.AVAILABLE);
        section.setSuggestions(snippets, CategoryStatus.AVAILABLE, /* replaceExisting = */ true);
        return section;
    }

    private SuggestionsSection createSectionWithReloadAction(boolean hasReloadAction) {
        CategoryInfoBuilder builder = new CategoryInfoBuilder(42).showIfEmpty();
        if (hasReloadAction) builder.withReloadAction();
        return createSection(builder.build());
    }

    private SuggestionsSection createSection(SuggestionsCategoryInfo info) {
        SuggestionsSection section = new SuggestionsSection(
                mDelegate, mUiDelegate, mock(SuggestionsRanker.class), mBridge, info);
        section.setParent(mParent);
        return section;
    }

    private OfflinePageItem createOfflinePageItem(String url, long offlineId) {
        return new OfflinePageItem(url, offlineId, "", "", "", 0, 0, 0, 0);
    }

    private static void verifyAction(SuggestionsSection section, @ActionItem.Action int action) {
        SuggestionsSource suggestionsSource = mock(SuggestionsSource.class);
        SuggestionsUiDelegate manager = mock(SuggestionsUiDelegate.class);
        SuggestionsNavigationDelegate navDelegate = mock(SuggestionsNavigationDelegate.class);
        when(manager.getSuggestionsSource()).thenReturn(suggestionsSource);
        when(manager.getNavigationDelegate()).thenReturn(navDelegate);
        when(manager.getMetricsReporter()).thenReturn(mock(SuggestionsMetricsReporter.class));

        if (action != ActionItem.ACTION_NONE) {
            section.getActionItem().performAction(manager);
        }

        verify(section.getCategoryInfo(),
                (action == ActionItem.ACTION_VIEW_ALL ? times(1) : never()))
                .performViewAllAction(navDelegate);
        verify(suggestionsSource,
                action == ActionItem.ACTION_RELOAD || action == ActionItem.ACTION_FETCH_MORE
                        ? times(1)
                        : never())
                .fetchSuggestions(anyInt(), any(String[].class));
    }
}
