// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.support.annotation.IntDef;
import android.view.View;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.ContextMenuManager.ContextMenuItemId;
import org.chromium.chrome.browser.ntp.ContextMenuManager.Delegate;
import org.chromium.chrome.browser.ntp.UiConfig;
import org.chromium.chrome.browser.ntp.snippets.CategoryInt;
import org.chromium.chrome.browser.ntp.snippets.SnippetsConfig;
import org.chromium.chrome.browser.suggestions.SuggestionsRanker;
import org.chromium.chrome.browser.suggestions.SuggestionsUiDelegate;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Item that allows the user to perform an action on the NTP.
 * Note: Use {@link #refreshVisibility()} to update the visibility of the button instead of calling
 * {@link #setVisible(boolean)} directly.
 */
public class ActionItem extends OptionalLeaf {
    @IntDef({ACTION_NONE, ACTION_VIEW_ALL, ACTION_FETCH_MORE, ACTION_RELOAD})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Action {}
    public static final int ACTION_NONE = 0;
    public static final int ACTION_VIEW_ALL = 1;
    public static final int ACTION_FETCH_MORE = 2;
    public static final int ACTION_RELOAD = 3;

    private final SuggestionsCategoryInfo mCategoryInfo;
    private final SuggestionsSection mParentSection;
    private final SuggestionsRanker mSuggestionsRanker;

    @Action
    private int mCurrentAction = ACTION_NONE;
    private boolean mImpressionTracked;
    private int mPerSectionRank = -1;

    public ActionItem(SuggestionsSection section, SuggestionsRanker ranker) {
        mCategoryInfo = section.getCategoryInfo();
        mParentSection = section;
        mSuggestionsRanker = ranker;
    }

    /** Call this instead of {@link #setVisible(boolean)} to update the visibility. */
    public void refreshVisibility() {
        mCurrentAction = findAppropriateAction();
        setVisible(mCurrentAction != ACTION_NONE);
    }

    @Override
    public int getItemViewType() {
        return ItemViewType.ACTION;
    }

    @Override
    protected void onBindViewHolder(NewTabPageViewHolder holder) {
        assert holder instanceof ViewHolder;
        mSuggestionsRanker.rankActionItem(this, mParentSection);
        ((ViewHolder) holder).onBindViewHolder(this);
    }

    @CategoryInt
    public int getCategory() {
        return mCategoryInfo.getCategory();
    }

    public void setPerSectionRank(int perSectionRank) {
        mPerSectionRank = perSectionRank;
    }

    public int getPerSectionRank() {
        return mPerSectionRank;
    }

    @VisibleForTesting
    void performAction(SuggestionsUiDelegate uiDelegate) {
        uiDelegate.getMetricsReporter().onMoreButtonClicked(this);

        switch (mCurrentAction) {
            case ACTION_VIEW_ALL:
                mCategoryInfo.performViewAllAction(uiDelegate.getNavigationDelegate());
                return;
            case ACTION_FETCH_MORE:
            case ACTION_RELOAD:
                uiDelegate.getSuggestionsSource().fetchSuggestions(
                        mCategoryInfo.getCategory(), mParentSection.getDisplayedSuggestionIds());
                mParentSection.onFetchStarted();
                return;
            case ACTION_NONE:
            default:
                // Should never be reached.
                assert false;
        }
    }

    @Action
    private int findAppropriateAction() {
        boolean hasSuggestions = mParentSection.hasSuggestions();
        if (mCategoryInfo.hasViewAllAction()) return ACTION_VIEW_ALL;
        if (hasSuggestions && mCategoryInfo.hasFetchMoreAction()) return ACTION_FETCH_MORE;
        if (!hasSuggestions && mCategoryInfo.hasReloadAction()) return ACTION_RELOAD;
        return ACTION_NONE;
    }

    /** ViewHolder associated to {@link ItemViewType#ACTION}. */
    public static class ViewHolder extends CardViewHolder implements ContextMenuManager.Delegate {
        private ActionItem mActionListItem;

        public ViewHolder(final NewTabPageRecyclerView recyclerView,
                ContextMenuManager contextMenuManager, final SuggestionsUiDelegate uiDelegate,
                UiConfig uiConfig) {
            super(R.layout.new_tab_page_action_card, recyclerView, uiConfig, contextMenuManager);

            itemView.findViewById(R.id.action_button)
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mActionListItem.performAction(uiDelegate);
                        }
                    });

            new ImpressionTracker(itemView, new ImpressionTracker.Listener() {
                @Override
                public void onImpression() {
                    if (mActionListItem != null && !mActionListItem.mImpressionTracked) {
                        mActionListItem.mImpressionTracked = true;
                        uiDelegate.getMetricsReporter().onMoreButtonShown(mActionListItem);
                    }
                }
            });
        }

        @Override
        public boolean isDismissable() {
            return SnippetsConfig.isSectionDismissalEnabled()
                    && !mActionListItem.mParentSection.hasSuggestions();
        }

        @Override
        protected Delegate getContextMenuDelegate() {
            return this;
        }

        @Override
        public void openItem(int windowDisposition) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeItem() {
            getRecyclerView().dismissItemWithAnimation(this);
        }

        @Override
        public String getUrl() {
            return null;
        }

        @Override
        public boolean isItemSupported(@ContextMenuItemId int menuItemId) {
            return menuItemId == ContextMenuManager.ID_REMOVE && isDismissable();
        }

        @Override
        public void onContextMenuCreated() {}

        public void onBindViewHolder(ActionItem item) {
            super.onBindViewHolder();
            mActionListItem = item;
        }
    }
}
